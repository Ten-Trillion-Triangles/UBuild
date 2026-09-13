package Colossal

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import Util.ProcessRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunColossalTaskTest
{
    @Test
    fun runsTheRequestedTaskInTheProjectAndReportsItsOutputAndFailure()
    {
        val root = Files.createTempDirectory("colossal-task-runner").toFile()
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try
        {
            val argsFile = File(root, "args.txt")
            val cwdFile = File(root, "cwd.txt")
            val wrapper = File(root, "gradlew")
            wrapper.writeText(
                """
                #!/bin/sh
                printf '%s\n' "${'$'}@" > '${argsFile.absolutePath}'
                pwd > '${cwdFile.absolutePath}'
                printf 'fake build output\n'
                exit 19
                """.trimIndent() + "\n",
            )
            check(wrapper.setExecutable(true))
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))

            runColossalSubprojectTask(root, "test")

            val output = captured.toString(Charsets.UTF_8)
            assertTrue(output.contains("Running: gradlew test in ${root.absolutePath}"))
            assertTrue(output.contains("fake build output"))
            assertTrue(output.contains("Task test exited with code 19."))
            assertEquals("test\n--console=plain", argsFile.readText().trim())
            assertEquals(root.canonicalPath, cwdFile.readText().trim())
        }
        finally
        {
            System.setOut(originalOut)
            root.deleteRecursively()
        }
    }


    @Test
    fun missingWrapperProducesBootstrapInstructionsWithoutStartingAProcess()
    {
        val root = Files.createTempDirectory("colossal-task-no-wrapper").toFile()
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try
        {
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))

            runColossalSubprojectTask(root, "build")

            assertTrue(captured.toString(Charsets.UTF_8).contains("Run `gradle wrapper` once"))
            assertFalse(File(root, "gradlew").exists())
        }
        finally
        {
            System.setOut(originalOut)
            root.deleteRecursively()
        }
    }


    @Test
    fun drainsLargeWrapperOutputWhileTheProcessIsRunning()
    {
        val root = Files.createTempDirectory("colossal-task-large-output").toFile()
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try
        {
            val wrapper = File(root, "gradlew")
            wrapper.writeText(
                """
                #!/bin/sh
                i=0
                while [ "${'$'}i" -lt 6000 ]; do
                    printf 'diagnostic-%04d-abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\n' "${'$'}i"
                    i=${'$'}((i + 1))
                done
                printf 'final task output\n'
                """.trimIndent() + "\n",
            )
            check(wrapper.setExecutable(true))
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))

            runColossalSubprojectTask(root, "build")

            val output = captured.toString(Charsets.UTF_8)
            assertTrue(output.length > 500_000, "Expected all large fake-wrapper output to be drained")
            assertTrue(output.contains("final task output"))
        }
        finally
        {
            System.setOut(originalOut)
            root.deleteRecursively()
        }
    }


    @Test
    fun terminatesAWrapperThatExceedsTheConfiguredTimeout()
    {
        val root = Files.createTempDirectory("colossal-task-timeout").toFile()
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        try
        {
            val wrapper = File(root, "gradlew")
            wrapper.writeText("#!/bin/sh\nexec sleep 10\n")
            check(wrapper.setExecutable(true))
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            val started = System.nanoTime()

            ProcessRuntime.withOverrides(
                processLauncher = { it.start() },
                colossalTaskTimeoutMillis = 50,
            ) {
                runColossalSubprojectTask(root, "test")
            }

            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue(captured.toString(Charsets.UTF_8).contains("Task test timed out after 30 minutes."))
            assertTrue(elapsedMillis < 5_000, "Expected the short test timeout to stop the wrapper promptly")
        }
        finally
        {
            System.setOut(originalOut)
            root.deleteRecursively()
        }
    }
}
