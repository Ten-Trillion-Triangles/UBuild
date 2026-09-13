package Parser

import Config.ColossalProject
import Config.GradleProject
import Config.Project
import Config.UnrealProject
import Globals.env
import TestSupport.UBuildTestEnvironment
import Util.CliIO
import Util.ProcessRuntime
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTypeDispatchTest
{
    @Test
    fun buildDispatchRunsGradleRootAndImplementedColossalSubprojectTasks()
    {
        UBuildTestEnvironment().use { testEnv ->
            val gradleRoot = File(testEnv.home, "projects/gradle").apply { mkdirs() }
            val colossalRoot = File(testEnv.home, "projects/colossal/autogenesis").apply { mkdirs() }
            val gradleLog = File(gradleRoot, "calls.log")
            val colossalLog = File(colossalRoot, "calls.log")
            installWrapper(gradleRoot, gradleLog)
            installWrapper(colossalRoot, colossalLog)

            registerProject("plain", GradleProject().apply {
                projectName = "plain"
                projectRoot = gradleRoot.absolutePath
                defaultTask = "assemble"
            })
            registerProject("colossal", ColossalProject().apply {
                projectName = "colossal"
                projectRoot = File(testEnv.home, "projects/colossal").absolutePath
                engineVersion = "colossal-1"
                isImplemented = true
                gradleSubproject = GradleProject().apply {
                    projectName = "autogenesis"
                    projectRoot = colossalRoot.absolutePath
                    defaultTask = "verifyAll"
                }
            })

            val launched = mutableListOf<Pair<String, String>>()
            ProcessRuntime.withOverrides({ builder ->
                launched += builder.directory().canonicalPath to builder.command().drop(1).joinToString(" ")
                builder.start()
            }) {
                assertTrue(dispatchTopLevelBuildByProjectType("plain"))
                assertTrue(dispatchTopLevelBuildByProjectType("colossal"))
            }

            assertEquals(
                listOf(
                    gradleRoot.canonicalPath to "assemble --console=plain",
                    colossalRoot.canonicalPath to "verifyAll --console=plain",
                ),
                launched,
            )
            assertEquals("${gradleRoot.canonicalPath}|assemble --console=plain", gradleLog.readText().trim())
            assertEquals("${colossalRoot.canonicalPath}|verifyAll --console=plain", colossalLog.readText().trim())
        }
    }

    @Test
    fun packageDispatchUsesDefaultStageWhenDiscoveryFindsNoStageTask()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = File(testEnv.home, "projects/no-stage-tasks").apply { mkdirs() }
            val calls = File(root, "calls.log")
            installWrapper(root, calls, tasksOutput = "Build tasks\n-----------\nbuild - Build project.\n")
            registerProject("plain", GradleProject().apply {
                projectName = "plain"
                projectRoot = root.absolutePath
                defaultStageTask = "bundleForRelease"
            })

            val launched = mutableListOf<List<String>>()
            val output = mutableListOf<String>()
            ProcessRuntime.withOverrides({ builder ->
                launched += builder.command().drop(1)
                builder.start()
            }) {
                CliIO.withAdapters({ error("No input is needed without stage candidates") }, output::add) {
                    assertTrue(dispatchTopLevelPackageByProjectType("plain"))
                }
            }

            assertEquals(
                listOf(
                    listOf("tasks", "--all", "--console=plain", "--no-daemon"),
                    listOf("bundleForRelease", "--console=plain"),
                ),
                launched,
            )
            assertTrue(output.any { it.contains("No stage-like tasks found") && it.contains("bundleForRelease") })
            assertEquals(
                "${root.canonicalPath}|tasks --all --console=plain --no-daemon\n" +
                    "${root.canonicalPath}|bundleForRelease --console=plain",
                calls.readText().trim(),
            )
        }
    }

    @Test
    fun packageDispatchHonorsStageOverrideAndNumericMenuForImplementedColossalProject()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = File(testEnv.home, "projects/autogenesis").apply { mkdirs() }
            val calls = File(root, "calls.log")
            installWrapper(root, calls, tasksOutput = "Build tasks\n-----------\nbuild - Build project.\ninstallDist - Create distribution.\n")
            registerProject("game", ColossalProject().apply {
                projectName = "game"
                projectRoot = File(testEnv.home, "projects/game").absolutePath
                engineVersion = "colossal-1"
                isImplemented = true
                gradleSubproject = GradleProject().apply {
                    projectName = "autogenesis"
                    projectRoot = root.absolutePath
                    defaultStageTask = "fallbackStage"
                }
            })

            val launched = mutableListOf<List<String>>()
            val output = mutableListOf<String>()
            ProcessRuntime.withOverrides({ builder ->
                launched += builder.command().drop(1)
                builder.start()
            }) {
                env.setArgs(arrayOf("package", "game", "--stage-task", "customStage"))
                assertTrue(dispatchTopLevelPackageByProjectType("game"))

                env.setArgs(arrayOf("package", "game"))
                CliIO.withAdapters({ "1" }, output::add) {
                    assertTrue(dispatchTopLevelPackageByProjectType("game"))
                }
            }

            assertEquals(
                listOf(
                    listOf("customStage", "--console=plain"),
                    listOf("tasks", "--all", "--console=plain", "--no-daemon"),
                    listOf("installDist", "--console=plain"),
                ),
                launched,
            )
            assertTrue(output.any { it.contains("Select a stage task for game") })
            assertTrue(output.any { it.contains("1. installDist") })
            assertEquals(
                "${root.canonicalPath}|customStage --console=plain\n" +
                    "${root.canonicalPath}|tasks --all --console=plain --no-daemon\n" +
                    "${root.canonicalPath}|installDist --console=plain",
                calls.readText().trim(),
            )
        }
    }

    @Test
    fun packageDispatchRejectsOutOfRangeStageChoiceWithoutRunningAProjectTask()
    {
        UBuildTestEnvironment().use { testEnv ->
            val root = File(testEnv.home, "projects/stages").apply { mkdirs() }
            val calls = File(root, "calls.log")
            installWrapper(root, calls, tasksOutput = "Build tasks\n-----------\ninstallDist - Create distribution.\n")
            registerProject("plain", GradleProject().apply {
                projectName = "plain"
                projectRoot = root.absolutePath
            })

            val launched = mutableListOf<List<String>>()
            val output = mutableListOf<String>()
            ProcessRuntime.withOverrides({ builder ->
                launched += builder.command().drop(1)
                builder.start()
            }) {
                CliIO.withAdapters({ "2" }, output::add) {
                    assertTrue(dispatchTopLevelPackageByProjectType("plain"))
                }
            }

            assertEquals(listOf(listOf("tasks", "--all", "--console=plain", "--no-daemon")), launched)
            assertEquals(
                listOf(
                    "Select a stage task for plain:",
                    "  1. installDist - Create distribution.",
                    "Enter a number, or type a task name directly:",
                    "Out of range.",
                ),
                output,
            )
            assertEquals("${root.canonicalPath}|tasks --all --console=plain --no-daemon", calls.readText().trim())
        }
    }

    @Test
    fun stubsAreHandledWhileUnrealAndUnknownAliasesFallThrough()
    {
        UBuildTestEnvironment().use { testEnv ->
            registerProject("colossal2", ColossalProject().apply {
                projectName = "colossal2"
                projectRoot = File(testEnv.home, "projects/colossal2").absolutePath
                engineVersion = "colossal-2"
                isImplemented = false
            })
            registerProject("unreal", UnrealProject().apply {
                projectName = "unreal"
                projectRoot = File(testEnv.home, "projects/unreal").absolutePath
            })
            val output = mutableListOf<String>()

            CliIO.withAdapters({ error("No input is required") }, output::add) {
                assertTrue(dispatchTopLevelBuildByProjectType("colossal2"))
                assertTrue(dispatchTopLevelPackageByProjectType("colossal2"))
                assertFalse(dispatchTopLevelBuildByProjectType("unreal"))
                assertFalse(dispatchTopLevelPackageByProjectType("unreal"))
                assertFalse(dispatchTopLevelBuildByProjectType("missing"))
                assertFalse(dispatchTopLevelPackageByProjectType("missing"))
            }

            assertEquals(2, output.count { it.contains("Colossal engine version 'colossal-2' is not yet implemented.") })
        }
    }

    private fun registerProject(alias: String, project: Project)
    {
        val engine = env.getDefaultEngine()
        engine.projects[alias] = project
        env.updateEngineConfig(engine)
    }

    private fun installWrapper(root: File, callsFile: File, tasksOutput: String = "")
    {
        val escapedCallsPath = callsFile.absolutePath.replace("'", "'\\''")
        val escapedTasksOutput = tasksOutput.replace("'", "'\\''")
        val script = File(root, "gradlew")
        script.writeText(
            """#!/bin/sh
                printf '%s|%s\n' "${'$'}PWD" "${'$'}*" >> '$escapedCallsPath'
                if [ "${'$'}1" = "tasks" ]; then
                    printf '%s' '$escapedTasksOutput'
                    exit 0
                fi
                printf 'fake task output\n'
            """.trimIndent() + "\n",
        )
        check(script.setExecutable(true)) { "Could not make fake Gradle wrapper executable" }
    }
}
