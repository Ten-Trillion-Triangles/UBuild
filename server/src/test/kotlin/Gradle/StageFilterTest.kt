package Gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the gradle stage-task filter used by the top-level `ubuild package` command.
 *
 * @since added in v2.
 */
class StageFilterTest
{
    @Test
    fun filterPicksInstallDistAndDistZip()
    {
        val tasks = listOf(
            GradleTaskDescriptor("compileKotlin", "build", "Compiles the kotlin code."),
            GradleTaskDescriptor("installDist", "distribution", "Installs the project."),
            GradleTaskDescriptor("distZip", "distribution", "Builds the ZIP distribution."),
            GradleTaskDescriptor("test", "verification", "Runs the tests."),
            GradleTaskDescriptor("build", "build", "Assembles the project."),
        )
        val stage = StageFilter.filter(tasks)
        assertEquals(2, stage.size)
        assertEquals("installDist", stage[0].name)
        assertEquals("distZip", stage[1].name)
    }


    @Test
    fun filterAcceptsCustomStageTaskBySubstring()
    {
        val tasks = listOf(
            GradleTaskDescriptor("myStage", "build", "Custom stage task."),
            GradleTaskDescriptor("compile", "build", "Compile."),
        )
        val stage = StageFilter.filter(tasks)
        assertEquals(1, stage.size)
        assertEquals("myStage", stage[0].name)
    }


    @Test
    fun filterIsCaseInsensitive()
    {
        val tasks = listOf(
            GradleTaskDescriptor("INSTALL", "build", "All caps install."),
            GradleTaskDescriptor("DistZip", "build", "Camel dist."),
        )
        val stage = StageFilter.filter(tasks)
        assertEquals(2, stage.size)
    }


    @Test
    fun filterIsEmptyForPlainBuildTasks()
    {
        val tasks = listOf(
            GradleTaskDescriptor("compileKotlin", "build", "x"),
            GradleTaskDescriptor("compileJava", "build", "x"),
            GradleTaskDescriptor("jar", "build", "x"),
            GradleTaskDescriptor("test", "verification", "x"),
        )
        val stage = StageFilter.filter(tasks)
        assertTrue(stage.isEmpty())
    }
}
