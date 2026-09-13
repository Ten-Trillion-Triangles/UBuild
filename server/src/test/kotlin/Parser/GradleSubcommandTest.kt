package Parser

import Config.GradleProject
import Config.UnrealProject
import Globals.env
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the `gradle run|test|clean` CLI commands with local fake wrappers.
 * These tests verify UBuild's dispatch and process arguments without invoking Gradle.
 */
class GradleSubcommandTest
{
    private lateinit var testEnvironment: UBuildTestEnvironment
    private lateinit var tempDir: File
    private lateinit var projectRoot: File
    private lateinit var markerRoot: File

    @BeforeTest
    fun setUp()
    {
        testEnvironment = UBuildTestEnvironment()
        tempDir = Files.createTempDirectory("ubuild-gradle-subcommands").toFile()
        projectRoot = File(tempDir, "project").apply { mkdirs() }
        markerRoot = File(tempDir, "invocations")
        installFakeWrapper(projectRoot, "direct")
        registerGradleProject("app", projectRoot, defaultTask = "assembleLocal")
    }

    @AfterTest
    fun tearDown()
    {
        testEnvironment.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun gradleRunUsesConfiguredDefaultTaskWhenPromptIsBlank()
    {
        env.setArgs(arrayOf("app"))
        CliIO.withAdapters({ "" }, {}, ::gradleRun)

        assertInvocation("direct", listOf("assembleLocal", "--console=plain"))
    }

    @Test
    fun gradleRunUsesTaskOverrideFromArguments()
    {
        env.setArgs(arrayOf("app", "checkIntegration"))
        CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, ::gradleRun)

        assertInvocation("direct", listOf("checkIntegration", "--console=plain"))
    }

    @Test
    fun gradleTestAndCleanUseTheirFixedTasks()
    {
        env.setArgs(arrayOf("app"))
        CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, ::gradleTest)
        assertInvocation("direct", listOf("test", "--console=plain"))

        env.setArgs(arrayOf("app"))
        CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, ::gradleClean)
        assertInvocation("direct", listOf("clean", "--console=plain"))
    }

    @Test
    fun gradleSubcommandDispatchConsumesVerbAndRunsRequestedHandler()
    {
        env.setArgs(arrayOf("run", "app", "customCheck"))
        CliIO.withAdapters({ error("Argument mode must not prompt") }, {}, ::dispatchGradleSubcommand)

        assertEquals(listOf("app", "customCheck"), env.getArgs(), "dispatch consumes only the subcommand verb")
        assertInvocation("direct", listOf("customCheck", "--console=plain"))
    }

    @Test
    fun unknownProjectDoesNotInvokeWrapperAndReportsMissingAlias()
    {
        env.setArgs(arrayOf("absent"))
        val output = captureOutput { gradleTest() }

        assertTrue(output.contains("Project 'absent' not found"), output)
        assertFalse(File(markerRoot, "direct.args").exists(), "unknown projects must not launch a wrapper")
    }

    @Test
    fun blankInteractiveAliasDoesNotInvokeWrapper()
    {
        env.setArgs(emptyArray())
        val lines = mutableListOf<String>()
        CliIO.withAdapters({ "   " }, lines::add, ::gradleClean)
        val output = lines.joinToString("\n")

        assertTrue(output.contains("Enter the project alias."), output)
        assertFalse(File(markerRoot, "direct.args").exists(), "blank aliases must not launch a wrapper")
    }

    @Test
    fun unrealProjectIsRejectedByGradleSubcommands()
    {
        val engine = env.getDefaultEngine()
        engine.projects["unreal"] = UnrealProject().apply {
            projectName = "unreal"
            projectRoot = this@GradleSubcommandTest.projectRoot.absolutePath
        }
        env.updateEngineConfig(engine)
        env.setArgs(arrayOf("unreal"))

        val output = captureOutput { gradleClean() }

        assertTrue(output.contains("Unreal projects do not support gradle subcommands"), output)
        assertFalse(File(markerRoot, "direct.args").exists(), "wrong project types must not launch a wrapper")
    }

    private fun captureOutput(block: () -> Unit): String
    {
        val lines = mutableListOf<String>()
        CliIO.withAdapters({ error("No interactive input expected") }, lines::add, block)
        return lines.joinToString("\n")
    }

    private fun assertInvocation(wrapperName: String, expectedArgs: List<String>)
    {
        val observedCwd = File(markerRoot, "$wrapperName.cwd").readText().trimEnd('\n', '\r')
        val observedArgs = File(markerRoot, "$wrapperName.args").readLines()
        assertEquals(projectRoot.absolutePath, observedCwd)
        assertEquals(expectedArgs, observedArgs)
    }

    private fun registerGradleProject(alias: String, root: File, defaultTask: String)
    {
        val engine = env.getDefaultEngine()
        engine.projects[alias] = GradleProject().apply {
            projectName = alias
            projectRoot = root.absolutePath
            this.defaultTask = defaultTask
        }
        env.updateEngineConfig(engine)
    }

    private fun installFakeWrapper(root: File, wrapperName: String)
    {
        markerRoot.mkdirs()
        val wrapper = File(root, "gradlew")
        wrapper.writeText(
            """#!/bin/sh
                |printf '%s\n' "${'$'}PWD" > '${File(markerRoot, "$wrapperName.cwd").absolutePath}'
                |printf '%s\n' "${'$'}@" > '${File(markerRoot, "$wrapperName.args").absolutePath}'
                |printf 'fake wrapper completed\n'
                |""".trimMargin(),
        )
        check(wrapper.setExecutable(true)) { "Could not mark fake wrapper executable" }
    }
}
