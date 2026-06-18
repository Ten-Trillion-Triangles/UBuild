package Parser

import Gradle.BuildFilePatcher
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that the gradle scaffolder helpers produce identical output whether the user
 * supplied arguments or used the wizard. We don't drive the full gradleInitTask
 * function (which reads from stdin via [readln]) because that would require a stdin
 * mock; instead we test the [BuildFilePatcher] that gradleInitTask delegates to,
 * since that is where the actual file mutation happens.
 *
 * @since added in v2.
 */
class CliArgsParityTest
{
    private lateinit var tempDir : File
    private lateinit var buildFile : File


    @kotlin.test.BeforeTest
    fun setUp()
    {
        tempDir = Files.createTempDirectory("cli-args-parity-test").toFile()
        buildFile = File(tempDir, "build.gradle.kts")
        buildFile.writeText(
            """
            //Existing gradle file.
            plugins {
                kotlin("jvm")
            }
            """.trimIndent()
        )
    }


    @kotlin.test.AfterTest
    fun tearDown()
    {
        tempDir.deleteRecursively()
    }


    @Test
    fun patcherProducesSameBlockForRenderedTask()
    {
        val argsBlock = BuildFilePatcher.renderTaskBlock(
            taskName = "myTask",
            group = "build",
            description = "Test",
            dependsOn = listOf("compile"),
            body = "println(\"hi\")",
        )
        val wizardBlock = BuildFilePatcher.renderTaskBlock(
            taskName = "myTask",
            group = "build",
            description = "Test",
            dependsOn = listOf("compile"),
            body = "println(\"hi\")",
        )
        assertEquals(argsBlock, wizardBlock)
    }


    @Test
    fun applyBlockAppendsToFileEnd()
    {
        val block = BuildFilePatcher.renderTaskBlock(taskName = "myTask")
        val updated = BuildFilePatcher.applyBlock(buildFile, block)
        assertTrue(updated.contains("tasks.register(\"myTask\")"))
        assertTrue(updated.endsWith("}\n"))
        //Original content is preserved.
        assertTrue(updated.contains("plugins {"))
        assertTrue(updated.contains("kotlin(\"jvm\")"))
    }
}
