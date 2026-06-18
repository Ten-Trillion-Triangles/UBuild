package Config

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Tests for the v1 -> v2 config migration. The whole point of the migration is that the
 * user never has to think about it: the on-disk file gets read, the v1 form is detected,
 * a backup is made, the v2 form is written, and the in-memory [ConfigFile] is returned
 * with every UE project intact.
 *
 * @since added in v2.
 */
class MigrationTest
{
    //We use a temp directory per test so the on-disk side effects (backup, rewrite) are
    //isolated. Without this the tests would clobber a real ~/.ubuild/config.json.
    private lateinit var tempDir : File
    private lateinit var configFile : File


    @BeforeTest
    fun setUp()
    {
        tempDir = Files.createTempDirectory("ubuild-migration-test").toFile()
        configFile = File(tempDir, "config.json")
    }


    @AfterTest
    fun tearDown()
    {
        tempDir.deleteRecursively()
    }


    /**
     * A v1 config has no `configVersion` field and its projects are stored without a
     * `type` discriminator. This is what a fresh v1 install looked like and what the
     * migration is designed to upgrade.
     */
    @Test
    fun migrateV1ToV2UpgradesProjectTypeAndSetsVersion()
    {
        //A captured v1 file shape. Note the absence of `configVersion` and the absence
        //of a `type` field on the project.
        val v1Json = """
            {
             "defaultConfig": "default",
             "loadedConfigKey": "default",
             "engineConfigs": {
              "default": {
               "engineRoot": "/path/to/engine",
               "buildSh": "/path/to/engine/Build.sh",
               "uat": "/path/to/engine/RunUAT.sh",
               "generatePath": "/path/to/engine/GenerateProjectFiles.sh",
               "versionSelectorPath": "/path/to/engine/UnrealVersionSelector",
               "version": "5.4",
               "projects": {
                "MyGame": {
                 "projectName": "MyGame",
                 "projectRoot": "/path/to/MyGame",
                 "archivePath": "/path/to/builds",
                 "projectTarget": "MyGame",
                 "uprojectPath": "/path/to/MyGame/MyGame.uproject",
                 "generatePathString": "/path/to/engine/GenerateProjectFiles.sh -project=/path/to/MyGame/MyGame.uproject -game -engine",
                 "switchVersionPathString": "/path/to/engine/UnrealVersionSelector -switchversion /path/to/MyGame/MyGame.uproject",
                 "defaultFlagAlias": "basic",
                 "defaultTarget": "",
                 "defaultPlatform": "",
                 "defaultConfig": "",
                 "buildStringList": {},
                 "packageList": {}
                }
               }
              }
             },
             "launchStrings": {},
             "flagAliasStrings": {
              "basic": "-prereqs -stage -pak -CrashReporter"
             }
            }
        """.trimIndent()

        configFile.writeText(v1Json)

        val upgraded = Migration.migrateIfNeeded(configFile)

        //In-memory result: configVersion promoted, project is now an UnrealProject.
        assertEquals(Migration.V2, upgraded.configVersion)
        val engine = upgraded.engineConfigs["default"]
        assertNotNull(engine)
        val project = engine.projects["MyGame"]
        assertNotNull(project)
        //The runtime type should be UnrealProject after the migration.
        val unrealProject = assertIs<UnrealProject>(project)
        assertEquals("MyGame", unrealProject.projectName)
        assertEquals("/path/to/MyGame", unrealProject.projectRoot)
        assertEquals("/path/to/builds", unrealProject.archivePath)
        assertEquals("MyGame", unrealProject.projectTarget)
        assertEquals("basic", unrealProject.defaultFlagAlias)

        //On-disk: backup exists, original was rewritten with the v2 form.
        val backup = File(tempDir, "config.json.v${Migration.V1}.bak")
        assertTrue(backup.exists(), "Expected v1 backup at ${backup.absolutePath}")
        assertEquals(v1Json, backup.readText().trim(),
            "Backup should be byte-identical to the original v1 file")

        val rewritten = configFile.readText()
        assertTrue(rewritten.contains("\"configVersion\""),
            "Rewritten file must declare configVersion: ${rewritten}")
        //The project should now have a type discriminator.
        assertTrue(rewritten.contains("\"type\"") && rewritten.contains("\"unreal\""),
            "Rewritten file must tag the project as an unreal project: ${rewritten}")
    }


    /**
     * Running the migration on an already-v2 file must be a no-op. The file is not
     * rewritten and no backup is created.
     */
    @Test
    fun migrateIfNeededIsNoOpOnV2()
    {
        val v2Json = """
            {
             "configVersion": 2,
             "defaultConfig": "default",
             "loadedConfigKey": "default",
             "engineConfigs": {
              "default": {
               "engineRoot": "/path/to/engine",
               "buildSh": "/path/to/engine/Build.sh",
               "uat": "/path/to/engine/RunUAT.sh",
               "generatePath": "/path/to/engine/GenerateProjectFiles.sh",
               "versionSelectorPath": "/path/to/engine/UnrealVersionSelector",
               "version": "5.4",
               "projects": {}
              }
             },
             "launchStrings": {},
             "flagAliasStrings": {}
            }
        """.trimIndent()

        configFile.writeText(v2Json)
        val before = configFile.readText()

        val parsed = Migration.migrateIfNeeded(configFile)

        //No-op: file unchanged, no backup created.
        assertEquals(Migration.V2, parsed.configVersion)
        assertEquals(before, configFile.readText(),
            "v2 file should not be rewritten by the migration")
        val backup = File(tempDir, "config.json.v${Migration.V1}.bak")
        assertTrue(!backup.exists(),
            "No backup should be created for an already-v2 file")
    }


    /**
     * An empty or completely missing file is not the migration's job; we leave it for
     * the calling [Globals.env.loadConfig] to handle (it creates a fresh default
     * ConfigFile in that case). Migration only fires on a file that has some content.
     */
    @Test
    fun migrateIfNeededErrorsCleanlyOnGarbage()
    {
        configFile.writeText("not valid json at all")

        var thrown : Throwable? = null
        try
        {
            Migration.migrateIfNeeded(configFile)
        }
        catch(t : Throwable)
        {
            thrown = t
        }

        //We expect an IllegalStateException (or a SerializationException wrapped in one)
        //because the migration can't recover a totally invalid file.
        assertNotNull(thrown, "Expected the migration to throw on garbage input")
    }
}
