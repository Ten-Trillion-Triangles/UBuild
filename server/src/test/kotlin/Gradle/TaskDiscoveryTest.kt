package Gradle

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(tasks.size >= 1)
        assertTrue(tasks.all { it.name == "build" })
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
        assertTrue(tasks.any { it.name == "build" })
        assertTrue(tasks.any { it.name == "another" })
    }
}
