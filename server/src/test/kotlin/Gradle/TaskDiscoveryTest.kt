package Gradle

import java.io.File
import java.nio.file.Files
import Util.ProcessRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [TaskDiscovery.parseTasksOutput]. The shelling-out portion is not unit-
 * tested here; the parser is the interesting bit and we test it against canned
 * gradle output that mirrors the real `tasks --all --console=plain` format.
 *
 * @since added in v2.
 */
class TaskDiscoveryTest
{
    @Test
    fun parseTasksOutputProducesAllGroupsAndTasks()
    {
        val sample = """
            Build tasks
            -----------
            assemble - Assembles the outputs of this project.
            build - Assembles and tests this project.
            clean - Deletes the build directory.

            Documentation tasks
            -------------------
            javadoc - Generates Javadoc API documentation for the main source code.

            Verification tasks
            ------------------
            check - Runs all checks.
            test - Runs the unit tests.
        """.trimIndent()

        val tasks = TaskDiscovery.parseTasksOutput(sample)
        val byName = tasks.associateBy { it.name }

        assertEquals(6, tasks.size)
        assertEquals("build", byName["build"]?.group)
        assertEquals("build", byName["assemble"]?.group)
        assertEquals("documentation", byName["javadoc"]?.group)
        assertEquals("verification", byName["test"]?.group)
        assertEquals("Assembles the outputs of this project.", byName["assemble"]?.description)
    }


    @Test
    fun parseTasksOutputIgnoresUnderlinesAndBlankLines()
    {
        val sample = """
            Build tasks
            -----------
            build - x

            build - y
        """.trimIndent()
        val tasks = TaskDiscovery.parseTasksOutput(sample)
        //The second `build` line has the same task name; we still parse it (gradle itself
        //emits one per group). The parser does not dedupe; that is the caller's job.
        assertEquals(
            listOf(
                GradleTaskDescriptor("build", "build", "x"),
                GradleTaskDescriptor("build", "build", "y"),
            ),
            tasks,
        )
    }


    @Test
    fun parseTasksOutputSkipsLinesWithoutSeparator()
    {
        val sample = """
            Build tasks
            -----------
            build - x
            this is not a task line
            another - not a header
        """.trimIndent()
        val tasks = TaskDiscovery.parseTasksOutput(sample)
        //The line without " - " should be ignored; the line with " - " but in the middle
        //is parsed as task "another".
        assertEquals(
            listOf(
                GradleTaskDescriptor("build", "build", "x"),
                GradleTaskDescriptor("another", "build", "not a header"),
            ),
            tasks,
        )
    }


    @Test
    fun discoverInvokesTheProjectWrapperAndParsesItsOutput()
    {
        val root = Files.createTempDirectory("gradle-discovery-wrapper").toFile()
        try
        {
            val argsFile = File(root, "args.txt")
            val cwdFile = File(root, "cwd.txt")
            createWrapper(root, """
                printf '%s\n' "${'$'}@" > '${argsFile.absolutePath}'
                pwd > '${cwdFile.absolutePath}'
                printf 'Build tasks\n-----------\nbuild - Builds the project.\ntest - Runs tests.\n'
            """.trimIndent())

            val discovered = TaskDiscovery.discover(root)

            assertEquals(listOf(
                GradleTaskDescriptor("build", "build", "Builds the project."),
                GradleTaskDescriptor("test", "build", "Runs tests."),
            ), discovered)
            assertEquals("tasks\n--all\n--console=plain\n--no-daemon", argsFile.readText().trim())
            assertEquals(root.canonicalPath, cwdFile.readText().trim())
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun discoverDrainsOutputLargerThanAnOperatingSystemPipeBeforeParsingTasks()
    {
        val root = Files.createTempDirectory("gradle-discovery-large-output").toFile()
        try
        {
            createWrapper(root, """
                i=0
                while [ "${'$'}i" -lt 6000 ]; do
                    printf 'diagnostic-%04d-abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\n' "${'$'}i"
                    i=${'$'}((i + 1))
                done
                printf 'Verification tasks\n------------------\ncheck - Runs checks.\n'
            """.trimIndent())

            val discovered = TaskDiscovery.discover(root)

            assertEquals(listOf(GradleTaskDescriptor("check", "verification", "Runs checks.")), discovered)
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun discoverReturnsNoTasksWhenTheWrapperFailsEvenIfItPrintedTaskLookingOutput()
    {
        val root = Files.createTempDirectory("gradle-discovery-failure").toFile()
        try
        {
            createWrapper(root, """
                printf 'Build tasks\n-----------\nbuild - Partial output.\n'
                exit 17
            """.trimIndent())

            assertTrue(TaskDiscovery.discover(root).isEmpty())
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun discoverStopsAndReturnsEmptyWhenTheWrapperExceedsItsTimeout()
    {
        val root = Files.createTempDirectory("gradle-discovery-timeout").toFile()
        try
        {
            createWrapper(root, "exec sleep 10")
            val started = System.nanoTime()

            val tasks = ProcessRuntime.withOverrides(
                processLauncher = { it.start() },
                taskDiscoveryTimeoutMillis = 50,
            ) {
                TaskDiscovery.discover(root)
            }

            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue(tasks.isEmpty())
            assertTrue(elapsedMillis < 5_000, "Expected the short test timeout to stop the wrapper promptly")
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun discoverTreatsWrapperLaunchFailureAsNoAvailableTasks()
    {
        val root = Files.createTempDirectory("gradle-discovery-launch-failure").toFile()
        try
        {
            createWrapper(root, "exit 0")

            val tasks = ProcessRuntime.withOverrides(
                processLauncher = { throw java.io.IOException("simulated launch failure") },
            ) {
                TaskDiscovery.discover(root)
            }

            assertTrue(tasks.isEmpty())
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun wrapperLookupRequiresAnExecutableUnixScriptAndPrefersItOverBatchFile()
    {
        val root = Files.createTempDirectory("gradle-wrapper-lookup").toFile()
        try
        {
            val unix = File(root, "gradlew")
            unix.writeText("#!/bin/sh\nexit 0\n")
            val batch = File(root, "gradlew.bat")
            batch.writeText("@echo off\r\nexit /b 0\r\n")
            assertFalse(unix.canExecute())
            assertEquals(batch.absoluteFile, TaskDiscovery.locateWrapper(root))

            unix.setExecutable(true)
            assertEquals(unix.absoluteFile, TaskDiscovery.locateWrapper(root))
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    private fun createWrapper(root : File, body : String) : File
    {
        val wrapper = File(root, "gradlew")
        wrapper.writeText("#!/bin/sh\nset -eu\n$body\n")
        check(wrapper.setExecutable(true)) { "Could not make fake wrapper executable" }
        return wrapper
    }
}
