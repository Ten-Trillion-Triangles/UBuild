package Config

import Globals.env
import Config.ColossalProject
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentLifecycleTest
{
    @Test
    fun saveLoadAndEngineSelectionPersistUnderTheConfiguredHome()
    {
        UBuildTestEnvironment().use {
            val engine = Engine().apply {
                engineRoot = "/fake/engine/nightly"
                projects["app"] = GradleProject().apply {
                    projectName = "app"
                    projectRoot = "/fake/app"
                    defaultTask = "verify"
                }
                projects["colossal"] = ColossalProject().apply {
                    projectName = "colossal"
                    projectRoot = "/fake/colossal"
                    engineVersion = "colossal-1"
                    gradleSubproject = GradleProject().apply {
                        projectName = "autogenesis"
                        projectRoot = "/fake/colossal/autogenesis"
                        defaultTask = "verifyAll"
                    }
                }
            }
            env.setDefaultEngine("nightly")
            env.updateEngineConfig(engine)

            val reloaded = env.loadConfig()

            assertEquals("nightly", reloaded.defaultConfig)
            assertEquals("nightly", reloaded.loadedConfigKey)
            assertEquals("/fake/engine/nightly", env.getDefaultEngine().engineRoot)
            assertEquals("verify", (env.getDefaultEngine().projects["app"] as GradleProject).defaultTask)
            assertEquals(
                "/fake/colossal/autogenesis",
                (env.getDefaultEngine().projects["colossal"] as ColossalProject).gradleSubproject.projectRoot,
            )
            assertTrue(File(it.home, ".ubuild/config.json").isFile)
            assertTrue(File(it.home, ".ubuild/config.json").readText().contains("\"gradle\""))
        }
    }

    @Test
    fun flagAliasCommandKeepsEveryArgumentInOrder()
    {
        UBuildTestEnvironment().use {
            env.setArgs(arrayOf("release", "-prereqs", "-stage", "-pak"))

            CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, env::setFlagAlias)

            assertEquals("-prereqs -stage -pak ", env.getFlagAlias("release"))
            val configFile = File(it.home, ".ubuild/config.json")
            assertTrue(configFile.readText().contains("\"release\""), "Alias missing from persisted config: ${configFile.readText()}")
            assertEquals(
                "-prereqs -stage -pak ",
                env.loadConfig().flagAliasStrings["release"],
                "Reloaded alias from ${configFile.absolutePath}; persisted contents: ${configFile.readText()}",
            )
        }
    }

    @Test
    fun flagAliasWizardPersistsCapturedAliasAndFlags()
    {
        UBuildTestEnvironment().use {
            env.setArgs(emptyArray())
            val answers = listOf("release", "-prereqs -pak").iterator()
            val output = mutableListOf<String>()

            CliIO.withAdapters({ answers.next() }, output::add, env::setFlagAlias)

            assertEquals("-prereqs -pak", env.loadConfig().flagAliasStrings["release"])
            assertEquals(listOf(
                "Enter a flag alias name.",
                "Enter any build flags you wish to add.",
                "Flag alias release set to -prereqs -pak",
            ), output)
            assertFalse(answers.hasNext())
        }
    }
}
