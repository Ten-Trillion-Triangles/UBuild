package Parser

import Colossal.runColossalSubprojectTask
import Config.ColossalProject
import Config.GradleProject
import Config.UnrealProject
import Globals.env
import Globals.env.getArgs
import Gradle.BuildFilePatcher
import Gradle.ProjectIntrospector
import Gradle.SubprojectScaffolder
import Gradle.TaskDiscovery
import Printer.printGradleHelp
import java.io.File

/**
 * Dispatch the `ubuild gradle <verb>` subcommand tree.
 *
 * The top-level [parseInput] calls this when the first argument is `gradle`. We consume
 * that token (it was already stripped by [parseInput]) and look at the next token to
 * pick the verb. If the user ran `ubuild gradle` with no further args we drop them into
 * the gradle wizard prompt.
 *
 * @since added in v2 alongside the [Config.GradleProject] type.
 */
fun dispatchGradleSubcommand()
{
    var verb = ""
    val args = getArgs()

    if(args.isNotEmpty())
    {
        verb = args[0]
        env.removeCommandFromArgs()
    }

    else
    {
        println("Enter a gradle subcommand. Or type help for a list. Or type back to return to the main menu.")
        verb = readln()
    }

    when(verb)
    {
        "help" -> printGradleHelp()
        "init-task" -> gradleInitTask()
        "init-subproject" -> gradleInitSubproject()
        "init-project" -> gradleInitProject()
        "list-tasks" -> gradleListTasks()
        "info" -> gradleInfo()
        "run" -> gradleRun()
        "test" -> gradleTest()
        "clean" -> gradleClean()
        "back" -> return
        else -> println("Unknown gradle subcommand: $verb. Type 'help' for a list.")
    }
}


/**
 * Resolve the project alias to either a [GradleProject] or a runnable
 * [ColossalProject] (with [ColossalProject.isImplemented] = true). Returns null if
 * the alias is unknown or the project is the wrong type.
 */
private fun resolveRunnableGradleProject(alias : String) : Pair<File, GradleProject>?
{
    val engine = env.getDefaultEngine()
    val project = engine.projects[alias] ?: run {
        println("Project '$alias' not found. Run set-project / colossal register first.")
        return null
    }
    return when(project)
    {
        is GradleProject -> Pair(File(project.projectRoot), project)
        is ColossalProject -> {
            if(!project.isImplemented)
            {
                println("Colossal engine version '${project.engineVersion}' is not yet implemented. " +
                        "Colossal 2 stubs refuse to run by design.")
                null
            }
            else
            {
                Pair(File(project.gradleSubproject.projectRoot), project.gradleSubproject)
            }
        }
        is UnrealProject -> {
            println("Project '$alias' is an Unreal project. Unreal projects do not support gradle subcommands.")
            null
        }
    }
}


/**
 * Wizard-mode scaffolder for adding a new `tasks.register { ... }` block to a project's
 * existing `build.gradle.kts`. Accepts program arguments or interactive prompts.
 *
 * @since added in v2.
 */
fun gradleInitTask()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty())
    {
        alias = args[0]
    }
    else
    {
        println("Enter the project alias.")
        alias = readln()
    }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, _) = resolved

    val taskName : String
    val group : String
    val description : String
    val dependsOnRaw : String
    val body : String

    if(args.size >= 5)
    {
        taskName = args[1]
        group = args[2]
        description = args[3]
        dependsOnRaw = args.getOrNull(4) ?: ""
        body = args.getOrNull(5) ?: ""
    }
    else
    {
        println("Enter the new task name (e.g. myTask).")
        taskName = readln()
        println("Enter the task group (or leave blank for none).")
        group = readln()
        println("Enter the task description (or leave blank).")
        description = readln()
        println("Enter comma-separated dependsOn task names (or leave blank).")
        dependsOnRaw = readln()
        println("Enter the doLast body (or leave blank for a TODO placeholder).")
        body = readln()
    }

    if(taskName.isBlank())
    {
        println("Task name cannot be blank.")
        return
    }

    val dependsOn = if(dependsOnRaw.isBlank()) emptyList()
    else dependsOnRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }

    val block = BuildFilePatcher.renderTaskBlock(
        taskName = taskName,
        group = group,
        description = description,
        dependsOn = dependsOn,
        body = body,
    )
    val buildFile = ProjectIntrospector.locateBuildFile(root)
    if(buildFile == null)
    {
        println("No build.gradle.kts or build.gradle found in ${root.absolutePath}.")
        return
    }
    val updated = BuildFilePatcher.applyBlock(buildFile, block)
    buildFile.writeText(updated)
    println("Added task '$taskName' to ${buildFile.absolutePath}.")
}


/**
 * Wizard-mode scaffolder for creating a new gradle subproject and registering it in the
 * project's `settings.gradle.kts`.
 *
 * @since added in v2.
 */
fun gradleInitSubproject()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the parent project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, _) = resolved

    val subprojectName : String
    val languageRaw : String
    if(args.size >= 3)
    {
        subprojectName = args[1]
        languageRaw = args[2]
    }
    else
    {
        println("Enter the new subproject name (e.g. server).")
        subprojectName = readln()
        println("Enter the starter language: kotlinJvm, kotlinMultiplatform, or java.")
        languageRaw = readln()
    }
    if(subprojectName.isBlank()) return

    val language = when(languageRaw.lowercase())
    {
        "kotlinmultiplatform", "multiplatform", "kmp" -> SubprojectScaffolder.Language.KOTLIN_MULTIPLATFORM
        "java" -> SubprojectScaffolder.Language.JAVA
        else -> SubprojectScaffolder.Language.KOTLIN_JVM
    }

    try
    {
        val newBuild = SubprojectScaffolder.scaffold(root, subprojectName, language)
        println("Scaffolded subproject '$subprojectName' at ${newBuild.absolutePath}.")
    }
    catch(e : IllegalArgumentException)
    {
        println("Failed: ${e.message}")
    }
    catch(e : IllegalStateException)
    {
        println("Failed: ${e.message}")
    }
}


/**
 * Wizard-mode scaffolder for creating a brand-new standalone gradle project.
 *
 * @since added in v2.
 */
fun gradleInitProject()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias to register under."); alias = readln() }
    if(alias.isBlank()) return

    val projectPath : String
    val languageRaw : String
    val group : String
    val version : String
    if(args.size >= 5)
    {
        projectPath = args[1]
        languageRaw = args[2]
        group = args[3]
        version = args[4]
    }
    else
    {
        println("Enter the directory to scaffold the new project into (will be created).")
        projectPath = readln()
        println("Enter the starter language: kotlinJvm, kotlinMultiplatform, or java.")
        languageRaw = readln()
        println("Enter the Maven group (e.g. com.example).")
        group = readln()
        println("Enter the version (e.g. 0.1.0).")
        version = readln()
    }

    if(projectPath.isBlank() || group.isBlank() || version.isBlank())
    {
        println("Project path, group, and version are all required.")
        return
    }

    val language = when(languageRaw.lowercase())
    {
        "kotlinmultiplatform", "multiplatform", "kmp" -> SubprojectScaffolder.Language.KOTLIN_MULTIPLATFORM
        "java" -> SubprojectScaffolder.Language.JAVA
        else -> SubprojectScaffolder.Language.KOTLIN_JVM
    }

    val rootDir = File(projectPath)
    Gradle.ProjectScaffolder.scaffold(rootDir, alias, group, version, language)
    println("Scaffolded standalone project at ${rootDir.absolutePath}.")
    println("Run `gradle wrapper` once (using a system gradle install) to generate gradlew/gradlew.bat.")
    println("Then run `ubuild gradle init-task $alias ...` to add tasks.")
}


/**
 * Shell out to `./gradlew tasks --all` for the project and print the result.
 *
 * @since added in v2.
 */
fun gradleListTasks()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, _) = resolved

    val tasks = TaskDiscovery.discover(root)
    if(tasks.isEmpty())
    {
        println("No tasks discovered. Check that ${root.absolutePath} has a gradlew script.")
        return
    }
    var currentGroup = ""
    for(task in tasks)
    {
        if(task.group != currentGroup)
        {
            currentGroup = task.group
            if(currentGroup.isNotBlank())
            {
                println()
                println("$currentGroup tasks")
                println("---------------")
            }
        }
        println("  ${task.name} - ${task.description}")
    }
}


/**
 * Print project / system / gradle properties plus env file contents and a static source
 * parse of `build.gradle.kts` for `val foo: Type by project(...)` declarations and
 * `System.getenv("...")` references.
 *
 * @since added in v2.
 */
fun gradleInfo()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, project) = resolved

    println("Project: $alias")
    println("  root: ${root.absolutePath}")
    println("  default task: ${project.defaultTask}")
    println("  default stage task: ${project.defaultStageTask}")

    val intro = ProjectIntrospector.introspect(root)

    println()
    println("Build-time project properties:")
    if(intro.projectProperties.isEmpty())
    {
        println("  (none found)")
    }
    else
    {
        for(prop in intro.projectProperties)
        {
            println("  ${prop.name}: ${prop.type} = \"${prop.defaultValue}\"")
        }
    }

    println()
    println("Environment variables referenced in build files:")
    if(intro.environmentVariables.isEmpty())
    {
        println("  (none found)")
    }
    else
    {
        for(env in intro.environmentVariables)
        {
            println("  $env")
        }
    }

    println()
    println("Env file entries:")
    if(intro.envFileEntries.isEmpty())
    {
        println("  (none found)")
    }
    else
    {
        var lastSource = ""
        for(entry in intro.envFileEntries)
        {
            if(entry.source != lastSource)
            {
                lastSource = entry.source
                println("  -- ${entry.source} --")
            }
            println("  ${entry.key} = ${entry.value}")
        }
    }
}


/**
 * Run the project's default gradle task (or the user-supplied one) via `./gradlew`.
 *
 * @since added in v2.
 */
fun gradleRun()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, project) = resolved

    val task : String
    if(args.size >= 2)
    {
        task = args[1]
    }
    else
    {
        println("Enter the task to run (blank for project default: ${project.defaultTask}).")
        val raw = readln()
        task = if(raw.isBlank()) project.defaultTask else raw
    }
    if(task.isBlank()) return

    runColossalSubprojectTask(root, task)
}


/**
 * Run the project's gradle `test` task.
 *
 * @since added in v2.
 */
fun gradleTest()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, _) = resolved
    runColossalSubprojectTask(root, "test")
}


/**
 * Run the project's gradle `clean` task.
 *
 * @since added in v2.
 */
fun gradleClean()
{
    val args = getArgs()
    val alias : String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the project alias."); alias = readln() }
    if(alias.isBlank()) return

    val resolved = resolveRunnableGradleProject(alias) ?: return
    val (root, _) = resolved
    runColossalSubprojectTask(root, "clean")
}
