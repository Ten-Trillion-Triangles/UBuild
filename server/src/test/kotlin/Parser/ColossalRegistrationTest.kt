package Parser

import Config.ColossalProject
import Config.GradleProject
import Globals.env
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColossalRegistrationTest
{
    @Test
    fun registersDetectedColossal1WithNestedGradleDefaultsAndPersistsIt()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = createColossal1Layout(testEnv.home)
            val output = mutableListOf<String>()
            env.setArgs(arrayOf("register", root.absolutePath, "--alias", "game"))

            CliIO.withAdapters({ error("Arguments provide all registration values") }, output::add) {
                dispatchColossalSubcommand()
            }

            val registered = assertIs<ColossalProject>(env.getDefaultEngine().projects["game"])
            assertEquals("game", registered.projectName)
            assertEquals(root.absolutePath, registered.projectRoot)
            assertEquals("${root.absolutePath}/build/staging", registered.archivePath)
            assertEquals("colossal-1", registered.engineVersion)
            assertTrue(registered.isImplemented)

            val gradle = registered.gradleSubproject
            assertEquals("game", gradle.projectName)
            assertEquals(root.absolutePath, gradle.projectRoot)
            assertEquals("${root.absolutePath}/build/staging", gradle.archivePath)
            assertEquals("build", gradle.defaultTask)
            assertEquals("installDist", gradle.defaultStageTask)

            val persisted = assertIs<ColossalProject>(env.loadConfig().engineConfigs["default"]?.projects?.get("game"))
            assertEquals("colossal-1", persisted.engineVersion)
            assertEquals("build", persisted.gradleSubproject.defaultTask)
            assertEquals("installDist", persisted.gradleSubproject.defaultStageTask)
            assertTrue(output.any { it.contains("Detected colossal-1") })
            assertTrue(output.any { it.contains("Registered colossal project 'game'") })
        }
    }

    @Test
    fun failedColossal1DetectionLeavesExistingConfigurationUntouched()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = File(testEnv.home, "workspace/not-colossal").apply { mkdirs() }
            File(root, "settings.gradle.kts").writeText("rootProject.name = \"plain\"\ninclude(\":server\")\n")
            val existing = GradleProject().apply {
                projectName = "existing"
                projectRoot = "/fake/existing"
            }
            val engine = env.getDefaultEngine().apply { projects["existing"] = existing }
            env.updateEngineConfig(engine)
            val configFile = File(testEnv.home, ".ubuild/config.json")
            val before = configFile.readText()
            val output = mutableListOf<String>()
            env.setArgs(arrayOf("register", root.absolutePath, "--alias", "should-not-register"))

            CliIO.withAdapters({ error("Arguments provide all registration values") }, output::add) {
                dispatchColossalSubcommand()
            }

            assertEquals(before, configFile.readText(), "Detection failure must not rewrite configuration")
            val projects = env.getDefaultEngine().projects
            assertIs<GradleProject>(projects["existing"])
            assertNull(projects["should-not-register"])
            assertTrue(output.any { it.contains("Detection failed") })
            assertTrue(output.any { it.contains("Matched settings markers") })
        }
    }

    @Test
    fun registersColossal2AsAnUnimplementedStubWithoutScanningThePath()
    {
        UBuildTestEnvironment().use { testEnv ->
            val missingRoot = File(testEnv.home, "future-engine/does-not-exist")
            val output = mutableListOf<String>()
            env.setArgs(
                arrayOf("register", missingRoot.absolutePath, "--alias", "future", "--version", "colossal-2"),
            )

            CliIO.withAdapters({ error("Arguments provide all registration values") }, output::add) {
                dispatchColossalSubcommand()
            }

            val registered = assertIs<ColossalProject>(env.getDefaultEngine().projects["future"])
            assertEquals("colossal-2", registered.engineVersion)
            assertFalse(registered.isImplemented)
            assertEquals(missingRoot.absolutePath, registered.projectRoot)
            assertEquals("${missingRoot.absolutePath}/build/staging", registered.archivePath)
            assertEquals("", registered.gradleSubproject.projectName)
            assertEquals("build", registered.gradleSubproject.defaultTask)
            assertTrue(output.any { it.contains("Registered colossal project 'future' (version=colossal-2)") })

            val persisted = assertIs<ColossalProject>(env.loadConfig().engineConfigs["default"]?.projects?.get("future"))
            assertFalse(persisted.isImplemented)
            assertEquals("colossal-2", persisted.engineVersion)
        }
    }

    @Test
    fun rejectsMissingOrInvalidVersionAndBlankWizardArgumentsWithoutSaving()
    {
        UBuildTestEnvironment().use { testEnv ->
            val validRoot = File(testEnv.home, "workspace/valid").apply { mkdirs() }
            val output = mutableListOf<String>()

            env.setArgs(arrayOf("register", validRoot.absolutePath, "--alias", "missing-version", "--version"))
            CliIO.withAdapters({ error("A missing --version value must return without prompting") }, output::add) {
                dispatchColossalSubcommand()
            }
            assertTrue(output.any { it.contains("--version requires a value") })
            assertNull(env.getDefaultEngine().projects["missing-version"])

            output.clear()
            env.setArgs(arrayOf("register", validRoot.absolutePath, "--alias"))
            CliIO.withAdapters({ error("A missing --alias value must return without prompting") }, output::add) {
                dispatchColossalSubcommand()
            }
            assertTrue(output.any { it.contains("--alias requires a value") })
            assertTrue(env.getDefaultEngine().projects.isEmpty())

            output.clear()
            env.setArgs(arrayOf("register", validRoot.absolutePath, "--alias", "invalid-version", "--version", "colossal-9"))
            CliIO.withAdapters({ error("Invalid version should be rejected before prompting") }, output::add) {
                dispatchColossalSubcommand()
            }
            assertTrue(output.any { it.contains("Unknown colossal version: colossal-9") })
            assertNull(env.getDefaultEngine().projects["invalid-version"])

            output.clear()
            env.setArgs(arrayOf("register"))
            val answers = ArrayDeque(listOf("", ""))
            CliIO.withAdapters({ answers.removeFirst() }, output::add) {
                dispatchColossalSubcommand()
            }
            assertTrue(output.any { it.contains("Enter the colossal project root") })
            assertTrue(output.any { it.contains("Enter the alias to register under") })
            assertTrue(output.any { it.contains("Path, alias, and version are all required") })
            assertEquals(0, answers.size)
            assertTrue(env.getDefaultEngine().projects.isEmpty())
        }
    }

    @Test
    fun colossalInfoShowsStoredFieldsAndFreshDetectionResults()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = createColossal1Layout(testEnv.home)
            env.setArgs(arrayOf("register", root.absolutePath, "--alias", "game"))
            CliIO.withAdapters({ error("Arguments provide all registration values") }, {}) {
                dispatchColossalSubcommand()
            }
            val output = mutableListOf<String>()
            env.setArgs(arrayOf("info"))

            CliIO.withAdapters({ "game" }, output::add) {
                dispatchColossalSubcommand()
            }

            assertTrue(output.any { it == "Colossal project: game" })
            assertTrue(output.any { it == "  engine version: colossal-1" })
            assertTrue(output.any { it == "  is implemented: true" })
            assertTrue(output.any { it == "  project root: ${root.absolutePath}" })
            assertTrue(output.any { it == "  archive path: ${root.absolutePath}/build/staging" })
            assertTrue(output.any { it == "  detection now: true" })
            assertTrue(output.any { it.contains("matched settings") })
            assertTrue(output.any { it.contains("matched TPipe siblings") })

            val futureRoot = File(testEnv.home, "future-colossal")
            env.setArgs(arrayOf("register", futureRoot.absolutePath, "--alias", "future", "--version", "colossal-2"))
            CliIO.withAdapters({ error("Arguments provide all registration values") }, {}) {
                dispatchColossalSubcommand()
            }
            val futureOutput = mutableListOf<String>()
            env.setArgs(arrayOf("info", "future"))
            CliIO.withAdapters({ error("Alias is provided") }, futureOutput::add) {
                dispatchColossalSubcommand()
            }
            assertTrue(futureOutput.any { it == "  engine version: colossal-2" })
            assertTrue(futureOutput.any { it == "  is implemented: false" })
            assertFalse(futureOutput.any { it.startsWith("  detection now:") })

            val notColossalOutput = mutableListOf<String>()
            val engine = env.getDefaultEngine().apply {
                projects["ordinary"] = GradleProject().apply { projectName = "ordinary" }
            }
            env.updateEngineConfig(engine)
            env.setArgs(arrayOf("info", "ordinary"))
            CliIO.withAdapters({ error("Alias is provided") }, notColossalOutput::add) {
                dispatchColossalSubcommand()
            }
            assertEquals(listOf("Project 'ordinary' is not a colossal project."), notColossalOutput)
        }
    }

    private fun createColossal1Layout(parent: File): File
    {
        val workspace = File(parent, "workspace").apply { mkdirs() }
        val root = File(workspace, "Autogenesis").apply { mkdirs() }
        File(workspace, "TPipe").mkdirs()
        File(root, "settings.gradle.kts").writeText(
            "rootProject.name = \"Autogenesis\"\ninclude(\":accelbyteSdk\")\ninclude(\":kvisionApp\")\n",
        )
        return root
    }
}
