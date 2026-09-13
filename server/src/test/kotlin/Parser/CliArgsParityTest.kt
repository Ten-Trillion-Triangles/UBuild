package Parser

import Config.GradleProject
import Globals.env
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that the actual argument and wizard paths create the same task block. */
class CliArgsParityTest
{
    private lateinit var testEnvironment: UBuildTestEnvironment
    private lateinit var tempDir: File
    private lateinit var argsBuildFile: File
    private lateinit var wizardBuildFile: File

    @BeforeTest
    fun setUp()
    {
        testEnvironment = UBuildTestEnvironment()
        tempDir = Files.createTempDirectory("cli-args-parity-test").toFile()
        argsBuildFile = newGradleProject("args-project")
        wizardBuildFile = newGradleProject("wizard-project")

        val engine = env.getDefaultEngine()
        engine.projects["args"] = GradleProject().apply {
            projectName = "args"
            projectRoot = argsBuildFile.parentFile.absolutePath
        }
        engine.projects["wizard"] = GradleProject().apply {
            projectName = "wizard"
            projectRoot = wizardBuildFile.parentFile.absolutePath
        }
        env.updateEngineConfig(engine)
    }

    @AfterTest
    fun tearDown()
    {
        tempDir.deleteRecursively()
        testEnvironment.close()
    }

    @Test
    fun argumentAndInteractiveModesProduceEquivalentTaskBlocks()
    {
        env.setArgs(arrayOf("args", "myTask", "build", "Run my task", "compile,test", "println(\"done\")"))
        CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, ::gradleInitTask)

        val wizardAnswers = listOf(
            "wizard", "myTask", "build", "Run my task", "compile,test", "println(\"done\")",
        ).iterator()
        env.setArgs(emptyArray())
        CliIO.withAdapters({ wizardAnswers.next() }, {}, ::gradleInitTask)

        assertEquals(argsBuildFile.readText(), wizardBuildFile.readText())
        assertEquals(false, wizardAnswers.hasNext())
    }

    private fun newGradleProject(name: String): File
    {
        val root = File(tempDir, name).apply { mkdirs() }
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"$name\"\n")
        val buildFile = File(root, "build.gradle.kts")
        buildFile.writeText("plugins { }\n")
        return buildFile
    }
}
