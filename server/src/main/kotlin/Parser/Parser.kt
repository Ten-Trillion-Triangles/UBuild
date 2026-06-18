package Parser

import Config.Engine
import Config.Project
import Config.UnrealProject
import Globals.env
import Globals.env.getArgs
import Globals.env.getDefaultEngine
import Globals.env.loadConfig
import Globals.env.swapEngineConfig
import Globals.env.updateEngineConfig
import java.io.File
import Printer.*
import Util.*
import kotlinx.datetime.*
import kotlin.system.exitProcess

/**Parser: This file is responsible for parsing user input and executing commands. 
 * Parser is tightly integrated with the Util library.
 * Parser supports parsing inputs through the cli wizard or executing commands from passed in arguments.
 *
 * Parser is responsible for:
 * -- Handling user input.
 * -- Parsing program arguments.
 * -- Executing commands.
 * -- Running Ubuild operations on the config system.
 * -- Piping programs into UBuilds output buffer.
 * -- Executing command line programs and shell scripts.
 */




/**
 * Convert unix/mac line endings to LF. This is required to bypass anti consumer bullshit in the msvc toolchain.
 * Msvc deliberately does not support "mac" line endings and does so to attempt to force you to only use windows
 * if you develop any software that supports windows. Sadly no decent or viable tools or software exists to do
 * a recursive line ending conversion on windows. So this function has to be part of ubuild to get around the issue.
 * @param rootDir The root directory to start recursively converting line endings. The paths will be walked via
 * symbolic links.
 */
fun unix2dos(rootDir : File)
{
    val files = getDirRecursive(rootDir)

    for(file in files)
    {
        convertLineEnding(file)
    }
}



/**
 * Set the engine configuration for the currently loaded engine config.
 * Will attempt to update in place if we find one at it's key. Otherwise, we'll create a new one.
 */
fun setEngine()
{
    val args = getArgs()
    var engineConfig = getDefaultEngine()
    var engineRoot = ""

    //Construct fresh config if null.
    if(engineConfig == null)
    {
        println("Warning: Engine config not found. Creating new engine config.")
        engineConfig = Engine()
    }

    if(args.isNotEmpty())
    {
        engineRoot = args[0] //Set engine has only one argument so it would always just be 0.
    }

    println("Enter the path to the engine's root folder. (The root folder contains Setup.sh and GenerateProjectFiles.sh)")
    engineRoot = readln()

    if(engineRoot.isNullOrEmpty())
    {
        error("Engine root path cannot be empty. Confirm the path you supplied has both Setup.sh and GenerateProjectFiles.sh files.")
        return
    }

    //Normalize and remove windows path delimiters
    engineRoot = engineRoot.replace("\\", "/")

    //Unreal engine's build systems ability to handle spaces is unstable at best. So we just have to not support it.
    if(engineRoot.contains(" "))
    {
        error("Engine root should not contain spaces." +
                "Escape chars have been added to attempt to fix this but no promises can be made here." +
                "Please remove all spaces from the path if possible.")
        engineRoot = "$engineRoot"
    }

    /**We need to remove the trailing slash so that combining paths later doesn't break things in UAT.
     * UAT fails to handle the trailing slash properly when it parses the build string so it's best to remove
     * problem here and now.
     */
    engineRoot = engineRoot.removeSuffix("/")
    engineConfig.engineRoot = engineRoot
    engineConfig.buildSh = engineRoot + "/Engine/Build/BatchFiles/${getOs()}/Build.sh"
    engineConfig.generatePath = engineRoot + "/Engine/Build/BatchFiles/${getOs()}/GenerateProjectFiles.sh"
    engineConfig.uat = engineRoot + "/Engine/Build/BatchFiles/RunUAT.sh"
    engineConfig.versionSelectorPath = engineRoot + "/Engine/Binaries/${getOs()}/UnrealVersionSelector"

    //If Windows, we need to convert to Windows file types.
    if(getOs() == "Win64")
    {
        engineConfig.buildSh = engineConfig.buildSh.replace(".sh", ".bat")
        engineConfig.generatePath = engineConfig.generatePath.replace(".sh", ".bat")
        engineConfig.uat = engineConfig.uat.replace(".sh", ".bat")
        engineConfig.versionSelectorPath = engineConfig.versionSelectorPath+".exe"
    }

    /**
     * For some reason on macOS the version selector is compiled in a shipping configuration.
     * When this happens UnrealBuildTool will rename the file making our attempt to locate it fail.
     * We need to handle this case and rename it in the shipping configuration naming scheme.
     */
    val shippingPath = File(engineConfig.versionSelectorPath)

    if(!shippingPath.exists())
    {
        engineConfig.versionSelectorPath = engineConfig.versionSelectorPath+"-${getOs()}-Shipping"

        //If Windows, we need to append .exe as well.
        if(getOs() == "Win64")
        {
            engineConfig.versionSelectorPath = engineConfig.versionSelectorPath+".exe"
        }
    }

    //Update and serialize the config file.
    env.updateEngineConfig(engineConfig)
    env.loadConfig()
    println("Engine configuration set!")

}


/**
 * Set project configurations for an unreal engine project.
 * Defines a user-friendly shorthand project alias for Ubuild when invoking builds.
 * Then gathers project data to generate the required syntax and build strings to invoke UBT and UAT
 * when building an Unreal Engine project on the command line. Can accept program augments or cli wizard.
 */
fun setProject()
{
    val engineConfig = getDefaultEngine()
    val args = getArgs()
    var projectAlias = "" //User defined alias to invoke the project in ubuild/
    var projectRoot = "" //Path to the root folder of the project
    var archivePath = "" //Path that UAT will dump packaged game files to. Default to the project root.
    var projectTarget = "" //Name of the Unreal project. EX: CCGToolkit instead of the alias.
    var project : UnrealProject? = engineConfig?.projects?.get(projectAlias) as? UnrealProject //Warning: This could be null!!
    val defaultFlagAlias = project?.defaultFlagAlias

    if(project == null)
    {
        project = UnrealProject() //Construct new object if not found.
    }


    if(engineConfig == null)
    {
        println("Engine config not found. Please call set-engine command first.")
        return
    }

    //Get our inputs from args if valid.
    if(args.isNotEmpty())
    {
        if(args.size >= 3)
        {
            projectAlias = args[0]
            projectRoot = args[1]
            projectTarget = args[2]

            //Archive path is optional and will default to the project root if not supplied.
            if(args.size >= 4)
            {
                archivePath = args[3]

                //Unreal engine's build systems ability to handle spaces is unstable at best. So we just have to not support it.
                if(archivePath.contains(" "))
                {
                    archivePath = "$archivePath"
                    println("Archive path should not contain any spaces. " +
                            "Escape chars have been added to attempt to fix this but no promises can be made here. " +
                            "Please remove all spaces from the path if possible.")
                }

                if(args.size >= 5)
                {
                    //Set default flag alias which will append more flags to the build string when packaging.
                    project.defaultFlagAlias = args[4]
                }
            }
        }

        else
        {
            error("Invalid arguments. set-project requires 2 arguments. The first is the project alias and the second is the project root.")
            return
        }

    }

    else
    {
        println("Enter the name of your project alias. This is a shortcut for ubuild to load your project settings." +
                "The project alias does not have to match the name of your Uproject file.")
        projectAlias = readln()

        println("Enter the name of your Unreal engine project EX: CCGToolkit instead of CCGToolkit.uproject")
        projectTarget = readln()

        println("Enter the path to your project root folder." +
                "Do not supply the absolute path to your Uproject file. Instead supply the folder that it's located in.")
        projectRoot = readln()

        println("Enter a path to dump your packaged game files to. This is optional and will default to the project root.")
        archivePath = readln()

        println("Enter a default flag alias. This will append more flags to the build string when packaging.")
        project.defaultFlagAlias = readln()
    }


    //Default to "basic" if nothing is inputted. This applies the common flags UnrealEditor does to all UAT builds.
    if(project.defaultFlagAlias.isNullOrEmpty())
    {
        project.defaultFlagAlias = "$defaultFlagAlias"
    }



    //Normalize and remove windows path delimiters
    projectRoot = projectRoot.replace("\\", "/")
    projectRoot = projectRoot.removeSuffix("/")

    //Unreal engine's build systems ability to handle spaces is unstable at best. So we just have to not support it.
    if(projectRoot.contains(" "))
    {
        projectRoot = "$projectRoot"
        error("Project root should not contain any spaces. " +
                "Escape chars have been added to attempt to fix this but no promises can be made here. " +
                "Please remove all spaces from the path if possible.")
    }

    //If the archive path is left blank we'll default to the project root.
    if(archivePath == "")
    {
        archivePath = projectRoot
    }

    //Normalize and remove windows path delimiters
    archivePath = archivePath.replace("\\", "/")

    //Assign root path values. These can be used construct all the program and build strings later.
    project?.projectName = projectAlias
    project?.projectRoot = projectRoot
    project?.archivePath = archivePath
    project?.projectTarget = projectTarget
    project?.uprojectPath = "$projectRoot/$projectTarget.uproject"
    project?.archivePath = archivePath

    project?.generatePathString = "${engineConfig.generatePath} -project=${project?.uprojectPath} -game -engine"
    project?.switchVersionPathString = "${engineConfig.versionSelectorPath} -switchversion ${project?.uprojectPath}"

    //Generate and serialize all package strings.
    generatePackageString(project, engineConfig)

    //Generate and serialize all build strings.
    generateBuildStrings(project, engineConfig)


    //Update and serialize the config file.
    engineConfig.projects[projectAlias] = project
    env.updateEngineConfig(engineConfig)
    env.loadConfig()

    println("Project configurations set!")

}


/**
 * Main function for the cli wizard mode.
 * Accepts user inputs and executes commands without program arguments.
 * In cli wizard mode, UBuild will remain running even after a command finishes
 * and be ready to accept a new command.
 * if, program arguments are passed in, they will be used instead.
 * At the end of the command execution UBuild will exit unless booted in server mode.
 */
fun parseInput()
{
    var command = ""
    var error = false
    var ranWithArgs = false

    if(env.getArgs().isNotEmpty())
    {
        command = env.getArgs()[0]
        env.removeCommandFromArgs()
        ranWithArgs = true
    }

    else
    {
        command = readln()
    }


    when(command) {
        "help" -> printHelp() //Prints a list of commands.
        "set-engine" -> setEngine()  //Set the engine configuration. Required to be done in setup for all other commands
        "set-project" -> setProject() //Set a project configuration.
        "exit" -> exitProcess(0) //Exit the program.
        "build" -> buildProject() //Build an unreal engine project. Equal to building from your IDE.
        "package" -> packageProject() //Package an unreal engine project.
        "module" -> buildModule() //Build an unreal engine module.
        "plugin" -> buildPlugin() //Build an unreal engine plugin.
        "generate" -> generateProjectFiles() //Generate project files.
        "switch" -> switchVersion() //Switch project to a different engine version.
        "set-launch" -> setLaunch() //Set a launch configuration for an external program.
        "run" -> runLaunch() //Run a saved launch config.
        "donate" -> setDonorPath() //Sets a donor path to collect engine version data from. Required for macOS.
        "swap" -> swap() //Switch to another engine configuration
        "default" -> default() //Set the default engine configuration
        "make" -> runMake() //Run the make command for unreal engine or equivalent for non Linux platforms.
        "register" -> register() //Register the engine with UAT.
        "flag-alias" -> flagAlias() //Add or update a flag alias
        "merge-flags" -> mergeFlagAliases() //Merge two flag aliases into a single one
        "list-flags" -> listFlags() //List all flag aliases
        "import" -> import() //Import project config as a template for another project
        "install" -> install() //Developer command to package up ubuild binaries in a deployment ready state.
        "path" -> path() //Update project path to a new path.
        "def-flags" -> generateDefaultFlags() //Used to un-fuck developer config files.
        "clear" -> Clear() //todo: Fix this so it actually works.
        "info" -> printResources() //Prints useful links and resources regarding ubt and uat.
        "set-uat" -> setUat() //Set a launch alias using the path to UAT and any arguments you want to pass to it.
        "zip-project" -> zipProject() //todo: Fix bug in UAT that prevents zipping up projects.
        "config"  -> config(false) //Print engine configuration information.
        "config-verbose" -> config(true) //Print engine configuration information with more detail.
        "remove-project" -> removeProject() //Delete a project configuration.
        "remove-engine" -> removeEngine() //Delete an engine configuration.
        "list-run" -> listRun() //Print all saved launch string configurations.
        else -> error = true
    }

    if(error)
    {
        println("Invalid command.")
    }

    //@update: Append date and time to help indicate when the command finished.
    else
    {
        val time =  Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        var hour = time.hour;
        if(hour > 12)
        {
            hour -= 12;
        }

        println("Operation Complete! @ ${time.date} ${hour}:${time.minute}")
    }

    if(!ranWithArgs)
    {
        parseInput()
    }

    else
    {
        exitProcess(0)
    }

}


fun generateProjectFiles()
{
    val args = getArgs()
    val engine = getDefaultEngine()
    var projectAlias = ""

    if(args.isNotEmpty())
    {
        projectAlias = args[0]
    }

    else
    {
        println("Enter the name of your project alias.")
        projectAlias = readln()
    }


    val project = engine.projects[projectAlias] as? UnrealProject

    if(project == null)
    {
        println("Project not found. Please enter a valid project alias.")
        return
    }

    val generateString = project.generatePathString
    val programString = splitProgramString(generateString)
    launchProgram(programString)

}

/**
 * Command to package an unreal engine project.
 * Either program arguments or the cli wizard will be used to collect the data needed to package the project.
 * Then, UAT or UBT will be run to package the project and will consume and block the main thread until complete.
 */
fun packageProject()
{
    val args = getArgs()
    val engine = getDefaultEngine()
    var projectAlias = "" //Ubuild project alias.
    var projectTarget = "" //Unreal engine project target. EX CCGToolkit, CCGToolkitServer, CCGToolkitEditor etc.
    var projectConfig = "" //Unreal engine project build configuration. EX Development, Shipping, DevelopmentEditor etc.
    var projectPlatform = "" //Unreal engine target platform/OS. EX Win64, Linux etc
    var flagAliasValue : String? = "" //The value of the flag alias command.

    if(args.isNotEmpty())
    {
        if(args.size >= 4)
        {
            projectAlias = args[0]
            projectTarget = args[1]
            projectConfig = args[2]
            projectPlatform = args[3]

            if(args.size >= 5)
            {
                //Get any extra build flags that might be being passed via arguments.
                flagAliasValue = getStringFromArgsbyIndex(args, 4)

                if(!env.getFlagAlias(flagAliasValue).isNullOrEmpty())
                {
                    /**This is pretty lazy but the basic idea is to get the value of the flag alias.
                     * It's less work right now to just do it this way even if it's a bit less explicit.
                     *
                     *@note for future me, basically ubuild is treating the argument as flags first.
                     * Then, if we try to test if it's a flag alias by just attempting to treat it as a key.
                     * If it's found, we basically set the var to again using the same call as in the if statement.
                     */
                    flagAliasValue = env.getFlagAlias(flagAliasValue)
                }
            }
        }

        else
        {
            println("At least 4 args are required for the package command. Please try again.")
            return
        }
    }

    else
    {
        println("Enter the name of your project alias.")
        projectAlias = readln()
        println("Enter the name of your project target or leave blank to default to the project's default target" +
                "EX: CCGToolkit, CCGToolkitServer, CCGToolkitEditor etc.")
        projectTarget = readln()
        println("Enter the name of your build configuration or leave blank to default to Development" +
                "EX: Development, Shipping, DevelopmentEditor etc.")
        projectConfig = readln()
        println("Enter your target platform or leave blank to default to your current OS" +
                "EX: Win64, Linux, Android, Mac, IOS")
        projectPlatform = readln()
    }




    val project = engine.projects[projectAlias] as? UnrealProject
    if(project == null)
    {
        println("Project not found. Please enter a valid project alias.")
        return
    }


    //Address default values it not assigned.
    if(projectTarget.isEmpty())
    {
        if(project.defaultTarget.isEmpty())
        {
            projectTarget = project.projectTarget
        }

        else
        {
            projectTarget = project.defaultTarget //Can be assigned using the import command to default to non-standard targets.
        }
    }


    if(projectConfig.isEmpty())
    {
        if(project.defaultConfig.isEmpty())
        {
            projectConfig = "Development"
        }

        else
        {
            projectConfig = project.defaultConfig //Can be assigned using the import command to default to non-standard configs.
        }

    }

    if(projectPlatform.isEmpty())
    {
        if(project.defaultPlatform.isEmpty())
        {
            projectPlatform = getOs()
        }

        else
        {
            projectPlatform = project.defaultPlatform //Can be assigned using the import command to default to non-standard platforms.
        }
    }

    /**
     * Package and build strings are stored in a nested data class configuration of BuildString().
     * The top level map is located in the Project() class. This proceeds to nest downwards into more maps.
     * With each BuildString() acting as a map for json serialization purposes.
     * Inside the BuildString() is the buildMap which first holds keys based on build configuration.
     * This holds another nested buildString() which then holds keys based on target platforms on it's innerMap
     * variable. The build strings are extracted by project -> config -> platform.
     * @see generateBuildStrings, generatePackageString, buildString() for more details.
     */
    val targetBuildString = project.packageList[projectTarget]
    val configBuildString = targetBuildString?.buildMap?.get(projectConfig)
    var platformBuildString = configBuildString?.innerMap?.get(projectPlatform)

    if(platformBuildString.isNullOrEmpty())
    {
        println("Could not find package string in packageProject @ Parser.kt for project: $projectAlias, target: $projectTarget")
        return
    }

    //Handle flag aliases and extra build flags.
    val flags = env.getConfig().flagAliasStrings[project.defaultFlagAlias]
    if(flags.isNullOrEmpty() && args.isEmpty())
    {
        println("Enter any additional package flags you wish to pass to UAT, or enter a flag alias to pass instead.")
        val flagAlias = readln()
        flagAliasValue = env.getConfig().flagAliasStrings[flagAlias]

        if(!flagAliasValue.isNullOrEmpty())
        {
            platformBuildString = "$platformBuildString $flagAliasValue"
        }

    }

    //Args must not be null if this passes. Treat the extracted arg string as the extra build flags.
    else if(flagAliasValue!!.isNotEmpty())
    {
        platformBuildString = "$platformBuildString $flagAliasValue"
    }

    else
    {
        //Use project default if not supplied.
        platformBuildString = "$platformBuildString $flags"
    }

    //Allow for multiple retries due to ispc bugs introduced in 5.5 and forward.
    var retryCount = if(flags?.contains("-retry") == true){3} else{0}

    var programSplit = splitProgramString(platformBuildString)
    launchProgram(programSplit, retryCount)

}

/**
 * Build an unreal engine project.
 * This is the same as building from your IDE.
 */
fun buildProject() {
    val engine = getDefaultEngine()
    val args = getArgs()
    var projectAlias = ""
    var projectTarget = ""
    var projectConfig = ""
    var projectPlatform = ""
    var extraFlags = ""

    if (args.isNotEmpty())
    {
        if (args.size >= 4)
        {
            projectAlias = args[0]
            projectTarget = args[1]
            projectConfig = args[2]
            projectPlatform = args[3]

            if(args.size >= 5)
            {
                extraFlags = args[4]
            }
        }

        else
        {
            println("At least 4 args are required for the build command. Please try again.")
            return
        }

    }

    println("Enter the name of your project alias.")
    projectAlias = readln()
    println("Enter the name of your project target or leave blank to default to the project's default target" +
            "EX: Editor, Server, Game, Client etc.")
    projectTarget = readln()
    println("Enter the name of your build configuration or leave blank to default to Development" +
            "EX: Develpopment, Shipping, DevelopmentEditor, Debug, DebugGame etc.")
    projectConfig = readln()
    println("Enter any additional flags or leave blank. EX: -rebuild -clean -cleanonly -fastrebuild -fastclean -lightrebuild -lightclean")
    extraFlags = readln()



    if(projectAlias.isEmpty())
    {
        println("Project alias cannot be empty.")
        return
    }


    //Attempt to resolve project alias from config file.
    val project = engine.projects[projectAlias] as? UnrealProject
    if(project == null)
    {
        println("Project not found. Please create the project first using set-project")
        return
    }

    //Handle default values if not assigned.
    if(projectTarget.isEmpty())
    {
        projectTarget = "Editor"
    }

    //Handle default build configuration
    if(projectConfig.isEmpty())
    {
        projectConfig = "Development"
    }

    //Attempt to unwind the nested maps.
    val outerBuildString = project.buildStringList[projectTarget] //Target is key, BuildString() is value.
    val innerBuildString = outerBuildString?.innerMap?.get(projectConfig) //Config is key, Build path string is value.

    if(innerBuildString.isNullOrEmpty())
    {
        println("Could not find build string in buildProject @ Parser.kt for project: $projectAlias, target: $projectTarget")
        return
    }

    var programSplit = splitProgramString(innerBuildString)
    var flags = splitProgramString(extraFlags)
    programSplit.addAll(flags)

    //Handle fast clean flags. If this returns false do not proceed with the build since the user just wants a fast clean.
    if(!parseFastCleanFlags(flags.toString(), project))
    {
        return //Exit at request of fast clean flag.
    }

    //Assign retry setting if desired.
    var retryCount = if(flags.contains("-retry")){3} else{0}

    launchProgram(programSplit, retryCount)

}


/**
 * Builds an unreal engine module.
 * Takes in either program arguments or the cli wizard will be used to collect the data needed to build the module.
 */
fun buildModule()
{
    val engine = getDefaultEngine()
    val buildSh = engine.buildSh
    var module = ""
    var platform = getOs()
    var config = "Development"
    var extraFlags = ""
    val args = getArgs()

    if(args.isNotEmpty())
    {
        if(args.size >= 3)
        {
            module = args[0]
            platform = args[1]
            config = args[2]

            if(args.size >= 4)
            {
                extraFlags = args[3]
            }
        }

        else
        {
            println("At least 3 args are required for the buildModule command. Please try again.")
            return
        }
    }

    else
    {
        println("Enter the name of your Unreal Engine module.")
        module = readln()
        println("Enter the name of your target platform or leave blank to default to your current OS" +
                "EX: Win64, Linux, Android, Mac, IOS")
        platform = readln()
        println("Enter the name of your build configuration or leave blank to default to Development" +
                "EX: Development, Shipping, DevelopmentEditor etc.")
        config = readln()
        println("Enter any additional flags or leave blank. EX: -rebuild -clean -cleanonly etc.")
        extraFlags = readln()

        if(platform.isEmpty())
        {
            platform = getOs()
        }

        if(config.isEmpty())
        {
            config = "Development"
        }
    }

    val argString = "$module $platform $config $extraFlags"
    val finalString = "$buildSh $argString"
    val programSplit = splitProgramString(finalString)
    launchProgram(programSplit)

}


/**
 * Switches a projects engine version.
 * Invokes the version selector if a version string is not saved.
 * If a version string is saved with the "donate" command it will be used
 * to manually overwrite the file instead.
 */
fun switchVersion()
{
    val engine = getDefaultEngine()
    val version = engine.version
    val versionSelector = engine.versionSelectorPath
    val args = getArgs()
    var projectAlias = ""

    if(args.isNotEmpty())
    {
        if(args.size >= 1)
        {
            projectAlias = args[0]
        }
    }

    else
    {
        println("Enter the name of your project alias.")
        projectAlias = readln()
    }

    if(projectAlias.isNotEmpty())
    {
        val project = engine.projects[projectAlias] as? UnrealProject
        if(project == null)
        {
            println("Project not found. Please enter a valid project alias.")
            return
        }

        val uprojectPath = project.uprojectPath
        val uprojectFile = File(uprojectPath)

        if(!uprojectFile.exists() && !uprojectFile.isFile)
        {
            println("Project not found in switchVersion @ Parser.kt" +
                    "Confirm the project alias is valid." +
                    "Invoke set-project command if not.")
            return
        }

        uprojectFile.setWritable(true)
        val uprojectString = uprojectFile.readText()
        val newLineSplit = uprojectString.split("\n").toMutableList()
        var newEngineId = ""
        var targetIndex = -1


        /**
         * Due to issues with macOS and sometimes Windows we may need to manually replace the version
         * inside the uproject file. Epic made it virtually impossible to distinguish how engine associations are generated
         * due to purposeful obfuscation in their own source code. To bypass this we use a donor uproject file
         * that already has the correct engine association. We then take that and store in the Engine() data class.
         * @see donate for more details.
         */
        if(engine.version.isNotEmpty())
        {
            //Search for the line holding our engine association value.
            for((index, line) in newLineSplit.withIndex())
            {
                if(line.contains("EngineAssociation"))
                {
                    targetIndex = index
                    break
                }

            }


            if(targetIndex != -1)
            {
                newEngineId = engine.version
                val engineAssociation = "\"EngineAssociation\":\" {${newEngineId}}\""
                newLineSplit[targetIndex] = engineAssociation
                uprojectFile.writeText(newLineSplit.joinToString("\n"))
                println("Switched version to $newEngineId")
                return
            }

            return
        }

        /**Pull the version selector string from the project data and launch it.
         * This is the default behavior if an engine version has not been set in the config file.
         * This works fine on Linux, and usually Windows. Won't work on macOS due to bugs on both Apple
         * and Epic's end.
         */
        val pathString = project.switchVersionPathString
        val programSplit = splitProgramString(pathString)
        launchProgram(programSplit)

    }
}

/**
 * Builds Unreal Engine itself.
 * The methods needed to do this vary from os to os.
 */
fun runMake()
{
    val os = getOs() //We need to deal with this in 3 different ways depending on the OS.
    val engine = getDefaultEngine()
    val buildSh = engine.buildSh
    val args = getArgs()
    var buildFlags = "DevelopmentEditor" //Build flags. EX: DevelopmentEditor, Development, Shipping, Debug, DebugGame.

    if(args.isNotEmpty())
    {
        if(args.size >= 1)
        {
            buildFlags = args[0]
        }

    }

    else
    {
        println("Enter the name of your build configuration or leave blank to default to DevelopmentEditor")
        buildFlags = readln()

        if(buildFlags.isEmpty())
        {
            buildFlags = "DevelopmentEditor"
        }
    }

    when(os)
    {
        "Win64" ->
            {

                /**@warning Due to process builder jank with java it's entirely possible that this won't work
                 * on windows. For some reason it needs the quotes here. This may result in process builder's
                 * behavior which cannot be changed, modified or fixed from the baffling decisions they made
                 * end up just not working at all. This is because it adds quotes to anything that has spaces on it.
                 * And because of that, I can't be certain of what will happen if you try to pass this argument
                 * on windows a Windows machine.
                 */

                //We need to invoke UAT to do command line builds on windows.
                val uatPath = engine.uat
                val argString = "BuildGraph -target=\"Make Installed Build Win64\" " +
                        "${engine.engineRoot}/Engine/Build/InstalledEngineBuild.xml $buildFlags"
                val finalString = "$uatPath $argString"
                val split = splitProgramString(finalString)
                launchProgram(split)
            }

        "Linux" ->
            {
                /**
                 * As of Unreal Engine 5.5 the make command no longer accepts program arguments. To get around this
                 * Linux builds will now also use build.sh directly instead of running make.
                 */
                val argString = "$buildFlags -buildscw"
                val finalString = "$buildSh $argString"
                launchProgram(splitProgramString(finalString))
            }

        else ->
            {
                //For macOS building via the command line is done by just building the editor as though it were a module.
                val argString = "$buildFlags -buildscw"
                val finalString = "$buildSh $argString"
                launchProgram(splitProgramString(finalString))
            }
    }

}


/**
 * Builds a project level plugin.
 * @note This cannot build any plugins inside the engine. This must be done by using the make command instead.
 */
fun buildPlugin()
{
    val engine = getDefaultEngine()
    val args = getArgs()
    var pluginName = ""
    var pluginTarget = ""
    var pluginConfig = ""
    var pluginPlatform = ""
    var projectAlias = ""

    if(args.isNotEmpty())
    {
        if(args.size >= 5)
        {
            projectAlias = args[0]
            pluginName = args[1]
            pluginTarget = args[2]
            pluginConfig = args[3]
            pluginPlatform = args[4]
        }

        else
        {
            println("At leat 4 args are required for the plugin command. Please try again.")
            return
        }
    }

    else
    {
        println("Enter the name of your plugin")
        pluginName = readln()
        println("Enter the target project alias for your plugin.")
        projectAlias = readln()
        println("Enter the build config for your plugin. EX: Development, Shipping, Debug, DebugGame, Test")
        pluginConfig = readln()
        println("Enter the platform for your plugin EX: Win64, Linux, Mac, IOS, Android")
        pluginPlatform = readln()

        if(pluginConfig.isEmpty())
        {
            pluginConfig = "Development"
        }

        if(pluginPlatform.isEmpty())
        {
            pluginPlatform = getOs()
        }
    }

    val project = engine.projects[projectAlias] as? UnrealProject

    if(project == null)
    {
        println("Project $projectAlias does not exist. Please create it using the set-project command.")
        return
    }

    val processString = engine.uat
    val argString = "BuildPlugin -Plugin=${project.projectRoot}/Plugins/$pluginName/$pluginName.uplugin " +
            "-Package=${project.archivePath}$pluginName $pluginConfig $pluginPlatform -CreateSubFolder -nocompile -nocompileuat"

    val finalString = "$processString $argString"
    launchProgram(splitProgramString(finalString))

}


/**
 * Register the loaded version of the engine with UAT.
 * This is rarely if ever needed, but on offer for full command line builds where the gui is not present.
 * In that case Setup.sh will be unable to register the engine with UAT because it requires the gui for some
 * baffling reason.
 */
fun register()
{
    val engine = getDefaultEngine()
    val uat = engine.uat

    if(uat.isNullOrEmpty())
    {
        println("No engine configuration found. Please create one using the set-engine command.")
        return
    }

    val processString = uat
    val argString = "-register -unattended"
    launchProgram(splitProgramString("$uat $argString"))
}

/**
 * Swaps to another engine configuration.
 * Also allows you to change the default configuration.
 * @note Does not support program arguments. Only works with the cli wizard.
 */
fun swap()
{
    val engine = getDefaultEngine()
    println("Enter an engine alias to swap to or create.")
    val alias = readln()

    swapEngineConfig(alias)

    println("Make this config default? (y/n)")
    val answer = readln()

    if(answer == "y")
    {
        env.setDefaultEngineConfig(alias)
    }

    loadConfig()
    println("Config swapped to $alias")
}

/**
 * Set the default engine configuration. Will result in that config always being laoded on startup.
 * Supports both program arguments and the cli wizard.
 */
fun default()
{
    val engine = getDefaultEngine()
    val args = getArgs()
    var newConfig = ""

    if(args.isNotEmpty())
    {
        newConfig = args[0]
    }

    else
    {
        println("Enter an engine alias to set as default.")
        newConfig = readln()
    }

    env.setDefaultEngineConfig(newConfig)
    println("Default config set to $newConfig")
}


/**
 * Reads a given uproject file and obtains the engine version from it.
 * This is required for macOS which is bugged both by apple and by Epic not
 * making registering engine versions functional on Mac.
 */
fun setDonorPath()
{
    val engine = getDefaultEngine()
    val args = getArgs()
    var uprojectPath = ""

    if(args.isNotEmpty())
    {
        uprojectPath = args[0]
    }

    else
    {
        println("Enter the path to your uproject file you wish obtain the engine version from.")
        uprojectPath = readln()
    }

    val path = File(uprojectPath)
    if(!path.exists())
    {
        println("The path you entered does not exist. Please try again.")
        return
    }

    if(path.isFile)
    {
        val uprojectFile = readStringFromFile(uprojectPath)
        val splitFileString = uprojectFile.split("\n").toList()

        for(i in splitFileString)
        {
            if(i.contains("EngineAssociation"))
            {
                val engineAssociation = i
                val version = engineAssociation.split(":").toList()[1]
                engine.version = version.replace("\"","") //Remove quotes so this can be stored a pure id.
                updateEngineConfig(engine)
                println("Engine version set to $version")
                return
            }
        }
    }

    println("Could not find EngineAssociation in $uprojectPath. Please try again.")
}

/**
 * Saves a launch shortcut. Useful for running build tasks quickly.
 * @note Only works with the cli wizard.
 */
fun setLaunch()
{
    println("Enter an alias for launch shortcut.")
    val alias = readln()
    println("Enter the path to your executable and include arguments")
    val path = readln()
    val newConfig = env.getConfig()
    newConfig.launchStrings[alias] = path
    env.updateConfigFile(newConfig)

    println("Launch shortcut $alias set to $path")
}


/**
 * Runs a saved launch config.
 * @note Only works with the cli wizard.
 */
fun runLaunch()
{
    val config = env.getConfig()

    println("Enter the launch config to run.")
    val alias = readln()
    val programString = config.launchStrings[alias] ?: ""
    val process = splitProgramString(programString)

    if(programString.isNotEmpty())
    {
        launchProgram(process)
    }

}


/**
 * Command that adds or updates a flag alias.
 */
fun flagAlias()
{
    env.setFlagAlias()
}


/**
 * Merges two flag alias strings together.
 */
fun mergeFlagAliases()
{
    val args = getArgs()
    var newAlias = ""
    var aliasA = ""
    var aliasB = ""

    if(args.size >= 3)
    {
        newAlias = args[0]
        aliasA = args[1]
        aliasB = args[2]
    }

    else
    {
        println("Enter a new flag alias name.")
        newAlias = readln()
        println("Enter the first flag alias to merge.")
        aliasA = readln()
        println("Enter the second flag alias to merge.")
        aliasB = readln()
    }

    val config = env.getConfig()
    val stringA = config.flagAliasStrings[aliasA] ?: ""
    val stringB = config.flagAliasStrings[aliasB] ?: ""
    val mergedString = "$stringA $stringB"
    config.flagAliasStrings[newAlias] = mergedString
    env.updateConfigFile(config)
    println("Flag alias $newAlias merged from $aliasA and $aliasB")
}

/**
 * Imports a ubuild project alias and uses it as the template for another ubuild project.
 * Only supports the cli wizard. Cannot be set from program arguments.
 */
fun import()
{
    val engine = getDefaultEngine()
    var projectAlias = ""
    var newProjectAlias = ""
    var projectRoot = ""
    var projectTarget = ""
    var archivePath = ""
    var defaultFlagAlias = ""
    var generateString = ""
    var switchVersionString = ""

    println("Enter the project alias to import.")
    projectAlias = readln()

    println("Enter the a new project alias.")
    newProjectAlias = readln()

    val oldProject = engine.projects[projectAlias] as? UnrealProject
    if(oldProject == null)
    {
        println("Project not found. Please enter a valid project alias.")
        return
    }

    projectRoot = oldProject.projectRoot
    projectTarget = oldProject.projectTarget

    println("Enter a path to dump your packaged game files to. This is optional and will default to the project root.")
    archivePath = readln()

    println("Enter a default flag alias. This will append more flags to the build string when packaging.")
    defaultFlagAlias = readln()

    //Default to "basic" if nothing is inputted. This applies the common flags UnrealEditor does to all UAT builds.
    if(defaultFlagAlias.isNullOrEmpty())
    {
        defaultFlagAlias = "$defaultFlagAlias"
    }

    //Normalize and remove windows path delimiters
    projectRoot = projectRoot.replace("\\", "/")
    projectRoot = projectRoot.removeSuffix("/")

    //Unreal engine's build systems ability to handle spaces is unstable at best. So we just have to not support it.
    if(projectRoot.contains(" "))
    {
        projectRoot = "$projectRoot"
        error("Project root should not contain any spaces. " +
                "Escape chars have been added to attempt to fix this but no promises can be made here. " +
                "Please remove all spaces from the path if possible.")
    }

    //If the archive path is left blank we'll default to the previous archive path that was set.
    if(archivePath == "")
    {
        archivePath = oldProject.archivePath
    }

    //Normalize and remove windows path delimiters
    archivePath = archivePath.replace("\\", "/")

    //Copy the imported values to the new project alias.
    val newProject = UnrealProject()
    newProject.projectRoot = projectRoot
    newProject.projectTarget = projectTarget
    newProject.archivePath = archivePath
    newProject.defaultFlagAlias = defaultFlagAlias
    newProject.switchVersionPathString = switchVersionString
    newProject.generatePathString = generateString
    newProject.defaultFlagAlias = defaultFlagAlias
    newProject.uprojectPath = oldProject.uprojectPath //This is hot garbage because it's inconsistent to how the other values are gotten.

    println("Assign a default project target if desired. This will be applied when packaging.")
    newProject.defaultTarget = readln()

    println("Assign a default project platform/os if desired. This will be applied when packaging.")
    newProject.defaultPlatform = readln()

    println("Assign a default project configuration if desired. This will be applied when packaging.")
    newProject.defaultConfig = readln()

    //Generate the build strings. And add them to the config file.
    generateBuildStrings(newProject, engine)
    generatePackageString(newProject, engine)

    //Update and serialize the config file.
    engine.projects[newProjectAlias] = newProject
    env.updateEngineConfig(engine)
    env.loadConfig()

    println("Project configurations set!")

}


//Print all saved flag aliases
fun listFlags()
{
    val config = env.getConfig()
    println(config.flagAliasStrings.toString().replace("{", "").replace("}", ""))
}


/**
 * Install script for UBuild binary deployments. Done here because it's easier to debug this in a proper ide
 * rather than dealing with bash which should just be avoided for any form of scripting if possible.
 */
fun install()
{
    val args = getArgs()
    val jarPath = getWorkingDirectory().removeSuffix("/")
    copyDir("$jarPath/server/build/libs/", "$jarPath/Ubuild/server/build/libs")
    copyFile("$jarPath/ubuild.sh", "$jarPath/Ubuild/ubuild.sh")
    copyFile("$jarPath/ubuild.bat", "$jarPath/ubuild/ubuild.bat")
    val jarFile = File("$jarPath/Ubuild/server/build/libs/server-all.jar")
    if(!jarFile.exists())
    {
        println("Ubuild2 could not find the jar file. Please make sure you have installed the project correctly.")
        return
    }

    println("Install complete!")
}


fun generateDefaultFlags()
{
    env.generateBuildFlagDefaults()
    env.updateConfigFile(env.getConfig())
}


/**
 * Update a project archive path.
 */
fun path()
{
    val engine = getDefaultEngine()
    val args = getArgs()
    var alias = ""
    var newPath = ""

    if(args.isNotEmpty())
    {
        println("Only cli wizard is supported with this command.")
        return
    }

    println("Enter the project alias you wish to change the archive path for.")
    alias = readln()
    println("Enter a new path for the archive path.")
    newPath = readln()


    val project = engine.projects[alias] as? UnrealProject

    if(project == null)
    {
        println("Project not found. Please create the project first using set-project.")
        return
    }


    project.archivePath = newPath
    engine.projects[alias] = project
    updateEngineConfig(engine)

    //We need to modify args in place here because set-project has to be called to actually save this change.
    val newArgs = arrayOf<String>("$alias", "${project.projectRoot}", "${project.projectTarget}", "${project.archivePath}", "${project.defaultFlagAlias}")

    /*Update project by calling with program arguments that have been faked above.
    This will replace and update the build strings to now hold our new archive path.
     */
    env.setArgs(newArgs)
    setProject()

    //We need to clear the args to stop ubuild from shutting down once this function exits.
    val emptyArgs = arrayOf<String>()
    env.setArgs(emptyArgs)

}


fun Clear()
{
    clearScreen()
}


/**
 * Sets a launch alias using the path to UAT and any arguments you want to pass to it.
 * supports the cli wizard only.
 */
fun setUat()
{
    println("Set which engine configuration you want to use. Or leave blank to use the default one.")
    var config = readln()

    if(config.isEmpty())
    {
        config = env.getConfig().defaultConfig
    }

    val configFile = env.getConfig()
    val uatPath = configFile.engineConfigs["$config"]?.uat

    println("Set a launch alias name for this UAT automation alias.\n\n")
    val alias = readln()

    println("Enter any arguments you want to pass to UAT.\n\n")
    val args = readln()
    val command = "$uatPath $args"

    configFile.launchStrings[alias] = command
    env.updateConfigFile(configFile)

    println("UAT alias set!")
}





fun zipProject()
{
    val args = getArgs()
    val engine = getDefaultEngine()
    var project = UnrealProject() //Default until loaded by arguments or cli wizard.
    var archivePath = "" //Possibly supplied in arguments so declare now just in case.
    var programString = "" //Full command to invoke uat and zip up our build.

    //Zip up project via arguments.
    if(args.isNotEmpty())
    {

        if(engine.projects.contains(args[0]))
        {
            project = engine.projects[args[0]] as UnrealProject!!
        }

        if(args.size >= 2)
        {
            archivePath = args[1]
        }

        else
        {
            archivePath = project.archivePath
        }

        programString = "${engine.uat} ZipProjectUp -nocompileeditor -project=${project.projectRoot.removeSuffix("/")} -install=${archivePath.removeSuffix("/")}.zip -nocompile -nocompileuat"
        val argsSplit = splitProgramString(programString)
        launchProgram(argsSplit)
        return
    }

    else
    {
        //Run cli wizard to gather our project settings.
        println("Enter the name of your ubuild project")
        val projectName = readln()

        if(!engine.projects.contains(projectName))
        {
            println("Failed to find project @zipProject in Parser.Kt")
            return
        }

        project = engine.projects[projectName] as UnrealProject!!

        println("Enter a an archive path or leave blank to use the project defaults")
        archivePath = readln()

        if(archivePath.isEmpty())
        {
            archivePath = project.archivePath
        }

        /**
         * @bug UAT is truncating the project path. UAT's function ZipProjectUp is where the culprit and needs
         * debugging. This is not our code so any unpatched versions of UAT will likely not work with this command.
         */
        programString = "${engine.uat} ZipProjectUp -nocompileeditor -install=\"${archivePath}.zip\" -project=\"${project.projectRoot.replace(" ", "")}\" -nocompile -nocompileuat"
        val argsSplit = splitProgramString(programString)
        launchProgram(argsSplit)
    }
}


/**
 * Perform a fast clean operation on the project. Instead of a ubt clean, this just deletes the binaries and
 * intermediate folders.
 */
fun fastClean(project : Project, deletePlugins : Boolean, deleteIntermediateOnly : Boolean)
{
    //Proceed only if we have a valid project root.
    if(!project.projectRoot.isEmpty())
    {
        var rootIntermediate = "${project.projectRoot}/Intermediate"
        var rootBinaries = "" //Cache now because we need to allow not deleting binaries.

        if(!deleteIntermediateOnly)
        {
            rootBinaries = "${project.projectRoot}/Binaries" //Allow if param is false
        }

        //At a minimum, we need to delete the intermediate folder. Otherwise, there's no point in the idea of a fast clean.
        deleteDir(rootIntermediate)

        if(deleteIntermediateOnly == false)
        {
            deleteDir(rootBinaries)
        }

        if(deletePlugins)
        {
            val pluginDirPaths = getDirRecursive(File("${project.projectRoot}/Plugins"))
            val validPluginDirs = mutableListOf<File>()

            //todo: This is returning the full path. We need to chop from the end and check then add if it fits.

            for(dir in pluginDirPaths)
            {

                /**
                 * Getting directories recursively returns the full path. We need to cut out that path and
                 * check to see the suffix is Intermediate or Binaries. Specifically, it must be the exact suffix.
                 * If not, and we delete an upper directory the directory in question will also be deleted.
                 * Should we attempt to delete it, it will throw an exception and crash ubuild. And for obvious reasons,
                 * allowing ubuild to ever crash is an antipattern to its design. It should be able to remain running
                 * crash free and allow the user to try again if they want to.
                 *
                 */
                var dirSuffix = ""
                val dirSplit = dir.toString().split("/").toList()
                if(dirSplit.isNotEmpty())
                {
                    dirSuffix = dirSplit.last() //Grab the last element. This should be the suffix and end of the path.
                }

                if(dir.isDirectory())
                {
                    /**
                     * Deleting binaries for plugins is not optional here because typically they are small enough
                     * to not matter to the point we absolutely must keep them. Unlike project binaries which matter
                     * for running from your ide at times, plugin binaries do not so this code is more readable and
                     * simple by leaving the option to not delete them off the table.
                     */
                    if(dirSuffix == "Intermediate" || dirSuffix.toString() == "Binaries")
                    {
                        validPluginDirs.add(dir)
                    }
                }
            }

            for(dir in validPluginDirs)
            {
                deleteDir(dir.toString())
            }
        }
    }


}


/**
 * Parse custom ubuild clean and rebuild flags. These flags add extra functionality to the clean and rebuild
 * arguments of ubt. Allowing ubuild to perform an intermediate level "fast clean" which only deletes the
 * intermediate and binaries folders at the project level instead of the entire engine.
 */
fun parseFastCleanFlags(flags : String, project : Project) : Boolean
{
    when {
        flags.contains("-fastrebuild") -> {
            fastClean(project, true, false)
            return true
        }

        flags.contains("-fastclean") -> {
            fastClean(project, true, false)
            return false
        }

        flags.contains("lightrebuild") -> {
            fastClean(project, false, true)
            return true
        }

        flags.contains("lightclean") -> {
            fastClean(project, false, true)
            return false
        }

        else -> {
            return true
        }
    }
}


fun config(verbose : Boolean)
{
    val config = env.getConfig()
    println("\n Loaded config key: " + config.loadedConfigKey)
    println("\n Current engine config: " + config.engineConfigs[config.loadedConfigKey]?.engineRoot)

    if(verbose)
    {
        println("Default config:" + config.defaultConfig)

        var projects = config.engineConfigs[config.loadedConfigKey]?.projects

        println("Projects:")
        for(project in projects!!)
        {
            println("\t" + project.key + ": " + project.value.projectRoot)
        }
    }
}



/**
 * Removes a project configuration from ubuild. Uses args or the cli wizard.
 * @since Projects are removed from the loaded engine configuration. So you need to make sure to swap
 * into the engine configuration you want to remove the project from.
 */
fun removeProject()
{
    val args = getArgs()
    val engine = getDefaultEngine()
    var projectName = ""

    if(args.isNotEmpty())
    {
        projectName = args[0]
    }

    else
    {
        println("Enter the name of the project you wish to remove.")
        projectName = readln()
    }

    val project = engine.projects[projectName] as? UnrealProject

    if(project == null)
    {
        println("Project not found. Please create the project first using set-project.")
    }

    else
    {
        engine.projects[projectName] = UnrealProject() //Stupid bug in kotlin surrounding removal of map values.
        engine.projects.remove(projectName)
        val config = env.getConfig()
        config.engineConfigs[config.loadedConfigKey] = engine
        env.updateConfigFile(config)
    }

}


/**
 * Remove an alternate engine configuration from ubuild. Engine must not be the root configuration.
 * Also, the engine must not be the currently loaded engine configuration.
 * @param args Uses the args or the cli wizard to get the name of the engine to remove.
 */
fun removeEngine()
{
    val args = getArgs()
    val config = env.getConfig()
    val currentEngine = getDefaultEngine()
    var engineName = ""

    if(args.isNotEmpty())
    {
        engineName = args[0]
    }

    else
    {
        println("Enter the name of the engine you wish to remove.")
        engineName = readln()
    }

    val targetEngine = config.engineConfigs[engineName]

    if(targetEngine == null || targetEngine == Engine())
    {
        println("Engine not found. Please create the engine first using set-engine.")
        return
    }

    if(engineName == "" || engineName == "default")
    {
        println("Root engine configuration cannot be removed. Please swap to another engine configuration first.")
        return
    }

    else
    {
        val pathChecksum = targetEngine.engineRoot
        val targetChecksum = currentEngine.engineRoot

        if(pathChecksum != targetChecksum)
        {
            config.engineConfigs.remove(engineName)
            env.updateConfigFile(config)
            return
        }

        println("The currently loaded engine config cannot be removed. Please swap to another engine configuration first.")
        return
    }
}


/**
 * Print all launch strings that are stored.
 */
fun listRun()
{
    var config = env.getConfig()
    var launches = config.launchStrings

    println("\n\nLaunch configurations:")

    for(i in launches)
    {
        println("${i.key}: ${i.value}\n")
    }
}
