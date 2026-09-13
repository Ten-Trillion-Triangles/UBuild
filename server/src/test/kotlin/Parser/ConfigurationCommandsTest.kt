package Parser

import Config.Engine
import Config.UnrealProject
import Globals.env
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import Util.ProcessRuntime
import Util.getOs
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests config-related CLI paths using isolated config homes and local fake programs only. */
class ConfigurationCommandsTest
{
    private lateinit var testEnvironment: UBuildTestEnvironment
    private val tempRoots = mutableListOf<File>()

    @BeforeTest
    fun setUp()
    {
        testEnvironment = UBuildTestEnvironment()
    }

    @AfterTest
    fun tearDown()
    {
        tempRoots.forEach { it.deleteRecursively() }
        testEnvironment.close()
    }

    @Test
    fun setProjectArgumentDefaultsGenerateCommandsAndPersistConfiguration()
    {
        val projectRoot = newTempRoot()
        val engine = configuredEngine()
        env.updateEngineConfig(engine)
        env.setArgs(arrayOf("sample", projectRoot.absolutePath + "/", "SampleGame"))

        CliIO.withAdapters({ error("set-project argument mode must not prompt") }, {}, ::setProject)

        val saved = env.getDefaultEngine().projects.getValue("sample") as UnrealProject
        val normalizedRoot = projectRoot.absolutePath
        val uproject = "$normalizedRoot/SampleGame.uproject"
        assertEquals("sample", saved.projectName)
        assertEquals(normalizedRoot, saved.projectRoot)
        assertEquals(normalizedRoot, saved.archivePath, "An omitted archive path defaults to the project root")
        assertEquals("SampleGame", saved.projectTarget)
        assertEquals(uproject, saved.uprojectPath)
        assertEquals("basic", saved.defaultFlagAlias)
        assertEquals(
            "${engine.generatePath} -project=$uproject -game -engine",
            saved.generatePathString,
        )
        assertEquals(
            "${engine.versionSelectorPath} -switchversion $uproject",
            saved.switchVersionPathString,
        )
        assertEquals(
            "${engine.buildSh} SampleGame ${getOs()} Development -Project=$uproject -buildscw",
            saved.buildStringList.getValue("").innerMap.getValue("Development"),
        )
        assertEquals(
            "${engine.uat} BuildCookRun -project=$uproject -ScriptsForProject=$uproject -noP4 " +
                "-Platform=Linux -clientconfig=Shipping -serverconfig=Shipping -cook -allmaps -build -stage " +
                "-archive -target=SampleGame -archivedirectory=$normalizedRoot",
            saved.packageList.getValue("SampleGame")
                .buildMap.getValue("Shipping").innerMap.getValue("Linux"),
        )
        assertTrue(File(testEnvironment.home, ".ubuild/config.json").readText().contains("\"sample\""))
    }

    @Test
    fun setProjectAcceptsExplicitArchiveAndDefaultFlagAlias()
    {
        val projectRoot = newTempRoot()
        val archiveRoot = newTempRoot()
        env.updateEngineConfig(configuredEngine())
        env.setArgs(
            arrayOf("sample", projectRoot.absolutePath, "SampleGame", archiveRoot.absolutePath, "shipping"),
        )

        CliIO.withAdapters({ error("set-project argument mode must not prompt") }, {}, ::setProject)

        val saved = env.getDefaultEngine().projects.getValue("sample") as UnrealProject
        assertEquals(projectRoot.absolutePath, saved.projectRoot)
        assertEquals(archiveRoot.absolutePath, saved.archivePath)
        assertEquals("shipping", saved.defaultFlagAlias)
        assertTrue(
            saved.packageList.getValue("SampleGame").buildMap.getValue("Shipping")
                .innerMap.getValue("Linux").endsWith("-archivedirectory=${archiveRoot.absolutePath}"),
        )
    }

    @Test
    fun setEngineWizardNormalizesRootAndPersistsDerivedToolPaths()
    {
        val engineRoot = newTempRoot()
        val enteredPath = engineRoot.absolutePath.replace("/", "\\") + "\\"
        val output = mutableListOf<String>()

        CliIO.withAdapters({ enteredPath }, { output.add(it) }, ::setEngine)

        val saved = env.getDefaultEngine()
        val os = getOs()
        val scriptExtension = if (os == "Win64") ".bat" else ".sh"
        val executableExtension = if (os == "Win64") ".exe" else ""
        assertEquals(engineRoot.absolutePath, saved.engineRoot)
        assertEquals(
            "${engineRoot.absolutePath}/Engine/Build/BatchFiles/$os/Build$scriptExtension",
            saved.buildSh,
        )
        assertEquals(
            "${engineRoot.absolutePath}/Engine/Build/BatchFiles/$os/GenerateProjectFiles$scriptExtension",
            saved.generatePath,
        )
        assertEquals(
            "${engineRoot.absolutePath}/Engine/Build/BatchFiles/RunUAT$scriptExtension",
            saved.uat,
        )
        assertEquals(
            "${engineRoot.absolutePath}/Engine/Binaries/$os/UnrealVersionSelector-$os-Shipping$executableExtension",
            saved.versionSelectorPath,
        )
        assertTrue(output.any { it.contains("Enter the path to the engine's root folder") })
        assertEquals("Engine configuration set!", output.last())
        assertTrue(File(testEnvironment.home, ".ubuild/config.json").readText().contains(engineRoot.absolutePath))
    }

    @Test
    fun setEngineArgumentsUseTheSuppliedPathWithoutReadingWizardInput()
    {
        val engineRoot = newTempRoot()
        val output = mutableListOf<String>()
        env.setArgs(arrayOf(engineRoot.absolutePath))

        CliIO.withAdapters({ error("set-engine argument mode must not prompt") }, output::add, ::setEngine)

        assertEquals(engineRoot.absolutePath, env.getDefaultEngine().engineRoot)
        assertFalse(output.any { it.startsWith("Enter the path to the engine's root folder") })
        assertTrue(File(testEnvironment.home, ".ubuild/config.json").readText().contains(engineRoot.absolutePath))
    }

    @Test
    fun buildProjectArgumentsLaunchTheConfiguredBuildWithoutReadingWizardInput()
    {
        val projectRoot = newTempRoot()
        val archiveRoot = newTempRoot()
        val fakeBuild = newTempRoot().resolve("Build.sh")
        val capturedArgs = newTempRoot().resolve("build-args.txt")
        fakeBuild.writeText(
            """#!/bin/sh
                |printf '%s\n' "${'$'}@" > '${capturedArgs.absolutePath}'
                |""".trimMargin(),
        )
        assertTrue(fakeBuild.setExecutable(true), "The fake build tool must be executable")

        env.updateEngineConfig(configuredEngine().apply { buildSh = fakeBuild.absolutePath })
        env.setArgs(
            arrayOf("sample", projectRoot.absolutePath, "SampleGame", archiveRoot.absolutePath, "basic"),
        )
        CliIO.withAdapters({ error("set-project argument mode must not prompt") }, {}, ::setProject)

        val output = mutableListOf<String>()
        val launchedCommands = mutableListOf<List<String>>()
        env.setArgs(arrayOf("sample", "", "Development", getOs()))
        ProcessRuntime.withOverrides({ builder ->
            launchedCommands.add(builder.command().toList())
            builder.start()
        }) {
            CliIO.withAdapters({ error("build argument mode must not prompt") }, output::add, ::buildProject)
        }

        val uproject = "${projectRoot.absolutePath}/SampleGame.uproject"
        val expectedCommand = listOf(
            fakeBuild.absolutePath,
            "SampleGameEditor",
            getOs(),
            "Development",
            "-Project=$uproject",
            "-buildscw",
        )
        assertEquals(listOf(expectedCommand), launchedCommands)
        assertEquals(expectedCommand.drop(1), capturedArgs.readLines())
        assertTrue(output.isEmpty(), "Argument mode must not print wizard prompts")
    }

    @Test
    fun setLaunchPersistsWizardEntryAndRunLaunchInvokesFakeExecutableWithExactArguments()
    {
        val executable = newTempRoot().resolve("fake-launcher.sh")
        val argumentCapture = newTempRoot().resolve("arguments.txt")
        executable.writeText("#!/bin/sh\nprintf '%s\\n' \"\$@\" > '${argumentCapture.absolutePath}'\n")
        assertTrue(executable.setExecutable(true), "The local fake executable must be runnable")
        val launchCommand = "${executable.absolutePath} --profile test --count=3"
        val setLaunchInput = listOf("local-check", launchCommand).iterator()
        val output = mutableListOf<String>()

        CliIO.withAdapters({ setLaunchInput.next() }, { output.add(it) }, ::setLaunch)

        assertFalse(setLaunchInput.hasNext())
        assertEquals(launchCommand, env.loadConfig().launchStrings.getValue("local-check"))
        assertEquals("Launch shortcut local-check set to $launchCommand", output.last())

        val runLaunchInput = listOf("local-check").iterator()
        val observedCommands = mutableListOf<List<String>>()
        ProcessRuntime.withOverrides({ builder ->
            observedCommands.add(builder.command().toList())
            builder.start()
        }) {
            CliIO.withAdapters({ runLaunchInput.next() }, { output.add(it) }, ::runLaunch)
        }

        assertFalse(runLaunchInput.hasNext())
        assertEquals(
            listOf(executable.absolutePath, "--profile", "test", "--count=3"),
            observedCommands.single(),
        )
        assertEquals(listOf("--profile", "test", "--count=3"), argumentCapture.readLines())
        assertEquals("Enter the launch config to run.", output.last())
    }

    @Test
    fun mergeFlagAliasesUsesThreeCommandArgumentsAndPersistsOrderedMerge()
    {
        val config = env.getConfig()
        config.flagAliasStrings["first"] = "-prereqs -stage "
        config.flagAliasStrings["second"] = "-pak -CrashReporter"
        env.updateConfigFile(config)
        env.setArgs(arrayOf("release", "first", "second", "ignored-extra"))
        val output = mutableListOf<String>()

        CliIO.withAdapters({ error("Three merge arguments must not prompt") }, { output.add(it) }, ::mergeFlagAliases)

        assertEquals("-prereqs -stage  -pak -CrashReporter", env.loadConfig().flagAliasStrings["release"])
        assertEquals("Flag alias release merged from first and second", output.last())
    }

    @Test
    fun mergeFlagAliasesWizardReadsNamesInOrderAndPersistsTheMergedValue()
    {
        val config = env.getConfig()
        config.flagAliasStrings["left"] = "-left"
        config.flagAliasStrings["right"] = "-right"
        env.updateConfigFile(config)
        val answers = listOf("combined", "left", "right").iterator()
        val output = mutableListOf<String>()

        CliIO.withAdapters({ answers.next() }, { output.add(it) }, ::mergeFlagAliases)

        assertFalse(answers.hasNext())
        assertEquals("-left -right", env.loadConfig().flagAliasStrings["combined"])
        assertTrue(output.any { it == "Enter a new flag alias name." })
        assertEquals("Flag alias combined merged from left and right", output.last())
    }

    @Test
    fun fastCleanFlagMappingReturnsBuildDecisionAndAppliesExpectedFilesystemEffects()
    {
        data class Case(
            val flag: String,
            val buildShouldContinue: Boolean,
            val deleteProjectBinaries: Boolean,
            val deleteProjectIntermediate: Boolean,
            val deletePluginCaches: Boolean,
        )

        val cases = listOf(
            Case("-fastrebuild", true, true, true, true),
            Case("-fastclean", false, true, true, true),
            Case("-lightrebuild", true, false, true, false),
            Case("-lightclean", false, false, true, false),
            Case("-clean", true, false, false, false),
        )

        for (case in cases)
        {
            val root = newTempRoot()
            val rootIntermediate = root.marker("Intermediate/cache")
            val rootBinary = root.marker("Binaries/module")
            val pluginIntermediate = root.marker("Plugins/Example/Intermediate/cache")
            val pluginBinary = root.marker("Plugins/Example/Binaries/module")
            val source = root.marker("Source/main.cpp")
            val project = UnrealProject().apply { projectRoot = root.absolutePath }

            val shouldContinue = parseFastCleanFlags(case.flag, project)

            assertEquals(case.buildShouldContinue, shouldContinue, "Return value for ${case.flag}")
            assertEquals(!case.deleteProjectIntermediate, rootIntermediate.exists(), "Root Intermediate for ${case.flag}")
            assertEquals(!case.deleteProjectBinaries, rootBinary.exists(), "Root Binaries for ${case.flag}")
            assertEquals(!case.deletePluginCaches, pluginIntermediate.exists(), "Plugin Intermediate for ${case.flag}")
            assertEquals(!case.deletePluginCaches, pluginBinary.exists(), "Plugin Binaries for ${case.flag}")
            assertTrue(source.exists(), "Source files must survive ${case.flag}")
        }
    }

    private fun configuredEngine(): Engine = Engine().apply {
        engineRoot = "/fake/Unreal"
        buildSh = "$engineRoot/Engine/Build/BatchFiles/Linux/Build.sh"
        generatePath = "$engineRoot/Engine/Build/BatchFiles/Linux/GenerateProjectFiles.sh"
        uat = "$engineRoot/Engine/Build/BatchFiles/RunUAT.sh"
        versionSelectorPath = "$engineRoot/Engine/Binaries/Linux/UnrealVersionSelector"
    }

    private fun newTempRoot(): File = Files.createTempDirectory("ubuild-config-command-test").toFile().also(tempRoots::add)

    private fun File.marker(relativePath: String): File = resolve(relativePath).apply {
        parentFile.mkdirs()
        writeText("fixture")
    }
}
