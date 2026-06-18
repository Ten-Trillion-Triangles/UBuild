package Parser

import Colossal.ColossalDetector
import Config.ColossalProject
import Config.GradleProject
import Globals.env
import Globals.env.getArgs
import Globals.env.getDefaultEngine
import Globals.env.updateEngineConfig
import Printer.printColossalHelp
import java.io.File

/**
 * Dispatch the `ubuild colossal <verb>` subcommand tree.
 *
 * The top-level [parseInput] calls this when the first argument is `colossal`. The verb
 * tree is intentionally small in v1 because most colossal operations fall through to
 * the gradle subcommand tree.
 *
 * @since added in v2 alongside the [Config.ColossalProject] type.
 */
fun dispatchColossalSubcommand()
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
        println("Enter a colossal subcommand. Or type help for a list. Or type back to return to the main menu.")
        verb = readln()
    }

    when(verb)
    {
        "help" -> printColossalHelp()
        "register" -> colossalRegister()
        "info" -> colossalInfo()
        "back" -> return
        else -> println("Unknown colossal subcommand: $verb. Type 'help' for a list.")
    }
}


/**
 * Register a colossal project. If `--version colossal-1` (the default) we run the
 * [ColossalDetector] against the user-supplied root; if `--version colossal-2` we
 * write a stub [ColossalProject] with `isImplemented = false`.
 *
 * @since added in v2.
 */
fun colossalRegister()
{
    val args = getArgs()

    //Parse arguments. We support `register <path> [--version <v>]` and the wizard path.
    var path: String? = null
    var version: String = "colossal-1"
    var alias: String? = null
    var i = 0
    while(i < args.size)
    {
        val a = args[i]
        when(a)
        {
            "--version" -> {
                if(i + 1 >= args.size)
                {
                    println("--version requires a value (colossal-1 or colossal-2).")
                    return
                }
                version = args[i + 1]
                i += 2
            }
            "--alias" -> {
                if(i + 1 >= args.size)
                {
                    println("--alias requires a value.")
                    return
                }
                alias = args[i + 1]
                i += 2
            }
            else -> {
                if(path == null) path = a
                i += 1
            }
        }
    }

    if(path == null)
    {
        println("Enter the colossal project root (e.g. /path/to/Autogenesis/Autogenesis).")
        path = readln()
    }
    if(alias == null)
    {
        println("Enter the alias to register under.")
        alias = readln()
    }
    if(version.isBlank())
    {
        println("Enter the colossal engine version (colossal-1 or colossal-2).")
        version = readln()
    }

    if(path.isNullOrBlank() || alias.isNullOrBlank() || version.isBlank())
    {
        println("Path, alias, and version are all required.")
        return
    }
    if(version != "colossal-1" && version != "colossal-2")
    {
        println("Unknown colossal version: $version. Use colossal-1 or colossal-2.")
        return
    }

    val project = if(version == "colossal-1")
    {
        buildColossal1Project(File(path), alias)
    }
    else
    {
        buildColossal2Stub(alias, path)
    }
    if(project == null) return

    val engine = getDefaultEngine()
    engine.projects[alias] = project
    updateEngineConfig(engine)
    env.loadConfig()
    println("Registered colossal project '$alias' (version=$version) at $path.")
}


/**
 * Build a [ColossalProject] for a colossal-1 root by running the detector.
 * Returns null if the detection fails and prints an error to the user.
 */
private fun buildColossal1Project(root : File, alias : String) : ColossalProject?
{
    val detection = ColossalDetector.detect(root)
    if(!detection.isColossal1)
    {
        println("Detection failed for $root. Need at least 2 colossal-1 settings markers and one TPipe sibling.")
        println("Matched settings markers: ${detection.matchedSettingsMarkers}")
        println("Matched TPipe siblings: ${detection.matchedTpipeSiblings}")
        return null
    }
    println("Detected colossal-1 at $root:")
    println("  matched settings: ${detection.matchedSettingsMarkers}")
    println("  matched TPipe siblings: ${detection.matchedTpipeSiblings}")

    val gradleSubproject = GradleProject().apply {
        this.projectName = alias
        this.projectRoot = root.absolutePath
        this.archivePath = "${root.absolutePath}/build/staging"
        this.defaultTask = "build"
        this.defaultStageTask = "installDist"
    }
    return ColossalProject().apply {
        this.projectName = alias
        this.projectRoot = root.absolutePath
        this.archivePath = "${root.absolutePath}/build/staging"
        this.engineVersion = "colossal-1"
        this.gradleSubproject = gradleSubproject
        this.isImplemented = true
    }
}


/**
 * Build a colossal-2 stub. The root path is recorded so future UBuild versions can
 * scan the directory, but [ColossalProject.isImplemented] is false so all commands
 * refuse to run.
 */
private fun buildColossal2Stub(alias : String, path : String) : ColossalProject
{
    return ColossalProject().apply {
        this.projectName = alias
        this.projectRoot = path
        this.archivePath = "$path/build/staging"
        this.engineVersion = "colossal-2"
        this.gradleSubproject = GradleProject()
        this.isImplemented = false
    }
}


/**
 * Show colossal-specific info about a registered project: engine version, the
 * underlying gradle subproject, and for colossal-1 the list of detected TPipe modules.
 *
 * @since added in v2.
 */
fun colossalInfo()
{
    val args = getArgs()
    val alias: String
    if(args.isNotEmpty()) alias = args[0]
    else { println("Enter the colossal project alias."); alias = readln() }
    if(alias.isBlank()) return

    val engine = getDefaultEngine()
    val project = engine.projects[alias]
    if(project !is ColossalProject)
    {
        println("Project '$alias' is not a colossal project.")
        return
    }
    println("Colossal project: $alias")
    println("  engine version: ${project.engineVersion}")
    println("  is implemented: ${project.isImplemented}")
    println("  project root: ${project.projectRoot}")
    println("  archive path: ${project.archivePath}")
    if(project.engineVersion == "colossal-1")
    {
        val detection = ColossalDetector.detect(File(project.projectRoot))
        println("  detection now: ${detection.isColossal1}")
        println("  matched settings: ${detection.matchedSettingsMarkers}")
        println("  matched TPipe siblings: ${detection.matchedTpipeSiblings}")
    }
}
