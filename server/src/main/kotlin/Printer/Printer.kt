package Printer

import Globals.env
import Globals.env.getArgs
import Util.clearScreen
import Util.executeBashCommand
import java.io.Console

/**
 * Welcome message when ubuild is booted in command line mode.
 * Will not be printed if main class has any args passed to it.
 */
fun printEntry()
{
    if(env.getArgs().isNotEmpty())
    {
            return
    }

    println("Welcome to UBuild. An automation tool for building ue projects from the command line.")
    println("Enter your command here. Or type help for a list of commands. Or type exit to close the program.")
}


fun printHelp() {
    val helpMap = mapOf(
        "set-engine" to """
           
           
            set-engine: Sets the path to your Build.sh file. Can be set with a path to the engine root
            or the absolute path to your engine.
            EX: /media/cage/LinuxStorage/YourUnrealEngineRootFolder/
            Abstract Example: ./ubuild.sh set-engine <path to your engine root>
            Live Example: ./ubuild.sh set-engine /media/cage/LinuxStorage/YourUnrealEngineRootFolder/
        """.trimIndent(),

        "set-project" to """
            
            
            set-project: Creates or updates a "project" that acts as an alias for the required command line build strings for UE.
            Calling from inside UBuild will provide a guided wizard. You can also call it from argument flags too.
            Abstract Example: ./ubuild.sh set project <Your project name here> <Name of your target file. EX: Project.uproject becomes Project> <File path to the root folder of your project> <Archive Path> <Extra Flags Alias>
            Live Example: ./ubuild.sh set-project revolution CCGToolkit /home/cage/Desktop/Workspaces/depot/ /media/builds/Revolution pac-chunk-flag
        """.trimIndent(),

        "build" to """
            
            
            build: Builds your project. If called inside UBuild a guided wizard will walk you through the params. Can also be called via arguments:
            
            Abstract Example: ./ubuild.sh build <your project name here> <Configuration> <Target>
            Live Example: ./ubuild.sh build revolution Development Editor
            
            Note: If the configuration and target are left blank it will default to DevelopmentEditor configuration.
            The build command now also accepts ubuild rebuild and clean flags -fastrebuild, -fastclean, -lightrebuild, -lightclean.
            These allow you to perform an intermediate level "fast clean" which only deletes the intermediate and binaries folders
            at the project level instead of the entire engine.
        """.trimIndent(),

        "generate" to """
            
            
            generate: Generates your project files. Can be invoked with the guided wizard or through argument flags
            Abstract Example: ./ubuild.sh generate <Project Name>
            Live Example: ./ubuild.sh generate revolution
        """.trimIndent(),

        "package" to """
            
            
            package: Packages an unreal engine project. WARNING: Android packaging may behave strangely and leave out
            Certain settings! The package command can be invoked using the cli wizard or by using argument flags:
            Abstract Example: ./UBuild package <project name> <platform name> <configuration> <target> <extra flags/flag alias>
            Live Example: ./ubuild.sh package revolution Linux Development CCGToolkit -rebulid
        """.trimIndent(),

        "module" to """
            
            
            module: Builds a specific Unreal Engine module.
            Can be invoked using the cli wizard or using args/flags.
            Abstract Example: ./ubuild.sh module <module name> <platform name> <configuration>
            Live Example: ./ubuild.sh module ShaderCompilerWorker Linux Development
        """.trimIndent(),


        "switch" to """
            
            
            switch: Switches Unreal Engine version for uproject. 
            Can be called using the cli wizard or command flags.
            Abstract Example: ./UBuild switch <project name>
            Live Example: ./ubuild.sh switch revolution
        """.trimIndent(),

        "make" to """
            
            
            make: Builds Unreal Engine directly. Can be called using the cli wizard or command flags.
            Abstract Example: ./ubuild.sh make <Build Flag>
            Live Example: ./ubuild.sh make -rebuild
        """.trimIndent(),

        "plugin" to """
            
            
            plugin: Builds a project level plugin or module. Can be called using the cli wizard or command flags.
            Abstract Example: ./ubuild.sh plugin <project name> <target plugin/module> <configuration>
            Live Example: ./ubuild.sh plugin revolution ConsoleX3 Development
        """.trimIndent(),

        "register" to """
            
            
            register: Registers the engine path set to UBuild to the UnrealVersionSelector. This may be required for MacOS users.
            The register command can only be called using the cli wizard currently.
        """.trimIndent(),

        "swap" to """
            
            
            swap: Switches to or creates a new secondary ubuild config file. This can be used to support 
            multiple engine versions at once. Supports the cli wizard only.
        """.trimIndent(),

        "default" to """
            
            
            default: Assigns the default ubuild configuration to load on startup. Must be called only if
            you're in the root configuration which is accessible by running swap without any arguments or passing
            no value through the cli wizard.
            
            Abstract Example: ./ubuild default <config name>
            Live Example: ./ubuild default 5.3
        """.trimIndent(),

        "path" to """
            
            
            path: Sets the archive path for a project.
            Abstract Example: ./ubuild.sh path <project name> <project path>
            Live Example: ./ubuild.sh path cotg /home/documents/cotg
        """.trimIndent(),

        "donate" to """
            
            
            donate: Enables the use of a donor uproject file to get the engine association.
            This is often needed in order to get around issues with switching versions on mac os.
            The engine id will be printed to the terminal.
            Abstract Example: ./ubuild.sh donate <project name>
            Live Example: ./ubuild.sh donate revolution
        """.trimIndent(),

        "set-launch" to """
            
            
            set launch: Sets a exe string to to memorize for later launches. 
            Supports cli wizard and program arguments.
            Abstract Example: ./ubuild.sh set-launch <alias> <launch string>
            nLive Example: ./ubuild.sh set-launch rev /home/cage/ws/depot/revolution -log -upload
        """.trimIndent(),

        "run" to """
            
            
            run: Runs a saved launch configuration. Can be called via the cli wizard 
            or program arguments.
            Abstract Example: ./UBuild run <alias>
            Live Example: ./UBuild run rev
        """.trimIndent(),

        "help" to """ 
            
            
            help: Prints a list of commands.
        """.trimIndent(),

        "flag-alias" to """
            
            
            flag-alias: Saves a series of extra build flags to an alias. This allows you to quickly append extra build flags.
            flag aliases can also be set as default flags to append to package strings when calling set-project.
            Abstract Example: ./ubuild.sh flag-alias <alias> <flag1> <flag2> <flag3>
            Live Example: ./ubuild.sh flag-alias pack-chunk-alias -rebuild -prereqs -createchunkinstall
        """.trimIndent(),

        "merge-flags" to """
            
            
            merge-flags: Merges two flag alias strings together.
            Abstract Example: ./ubuild.sh merge-flags <new flag alias> <flag alias 1> <flag alias 2>
            Live Example: ./ubuild.sh merge-flags pack-chunk-alias pack-chunk-alias2 pack-chunk-alias3
        """.trimIndent(),

        "list-flags" to """
            
            
            list-flags: Prints all flag aliases.
        """.trimIndent(),

        "import" to """
            
            
            import: Creates a new project alias using an existing one as a template. Will automatically copy over
            the project target, and uproject path, as well as the generate, and switch version string. Archive path
            can be defaulted to the originally set archive path if desired. Only the cli wizard is supported for this
            command.
        """.trimIndent(),

        "path" to """
            
            
            
            path: Updates the archive path for a project. Supports cli wizard only.
            Abstract Example: ./ubuild.sh path <project name> <project path>
            Live Example: ./ubuild.sh path cotg /home/documents/cotg
        """.trimIndent(),

        "clear" to  """
            
            
            
            clear: Clears the terminal. Supports cli wizard only.""".trimIndent(),

        "info" to """
            
            
            
            info: Prints helpful links and resources surrounding how ubt and uat works.
        """.trimIndent(),


        "set-uat" to """
            
            
            set-uat: Sets a launch alias using the path to UAT and any arguments you want to pass to it. Useful for 
            running uat tasks quickly. Supports the cli wizard only.
        """.trimIndent(),


        "zip-project" to """
            
            
            zip-project: Zips up a project. Supports cli wizard and program arguments.
            Abstract Example: ./ubuild.sh zip-project <project name> <archive path>
            Live Example: ./ubuild.sh zip-project cotg /home/documents/cotg
        """.trimIndent(),


        "remove-project" to """
            
            
            remove-project: Removes a project from ubuild. Supports cli wizard and program arguments.
            Abstract Example: ./ubuild.sh remove-project <project name>
            Live Example: ./ubuild.sh remove-project cotg
            
            Note: In order to remove a project you must be swapped to the engine configuration it is in. If you are
            calling this from the command line ensure the default command has been set prior to point ubuild
            to the config you want. Otherwise run the swap command to your target engine configuration from the
            cli wizard.
        """.trimIndent(),


        "list-run" to """
            
            
            list-run: Prints all saved launch strings. Does not contain any arguments. Can be invoked using cli
            wizard or arguments.
        """.trimIndent(),

        "gradle" to """
            
            
            gradle: Routes to the gradle subcommand tree. See `ubuild gradle help` for the full list.
            Supports the init-task, init-subproject, init-project, list-tasks, info, run, test, and clean verbs.
            Abstract Example: ./ubuild.sh gradle <verb> [args]
            Live Example: ./ubuild.sh gradle list-tasks MyGame
        """.trimIndent(),

        "colossal" to """
            
            
            colossal: Routes to the colossal subcommand tree. See `ubuild colossal help` for the full list.
            Supports the register and info verbs. Colossal 2 is registered as a stub that fails loud.
            Abstract Example: ./ubuild.sh colossal <verb> [args]
            Live Example: ./ubuild.sh colossal register /path/to/Autogenesis/Autogenesis --alias Autogenesis
        """.trimIndent()
    )


    val args = getArgs()
    if(args.isNotEmpty())
    {
        val command = args[0]
        println(helpMap[command])
    }

    else
    {
        println(helpMap.toString().replace("{","").replace("}", ""))

    }

}


fun printResources()
{
    println("""UBT (Unreal Build Tool): 
        |
        | https://github.com/Allar/compiling-unreal
        | https://dev.epicgames.com/documentation/en-us/unreal-engine/unreal-build-tool-in-unreal-engine
        | https://dev.epicgames.com/documentation/en-us/unreal-engine/unreal-engine-command-line-arguments-reference
    """.trimMargin())

    println("""
        |
        |UAT (Unreal Automation Tool):
        |
        |https://github.com/botman99/ue4-unreal-automation-tool
        """.trimMargin())
}


/**
 * Print the gradle subcommand help.
 *
 * Called by `ubuild help` and `ubuild gradle help`. Mirrors the format of [printHelp]
 * so the per-command entries are consistent.
 *
 * @since added in v2 alongside the gradle subcommand tree.
 */
fun printGradleHelp()
{
    val helpMap = mapOf(
        "init-task" to """
            init-task: Add a new gradle task to an existing build.gradle.kts in a registered
            gradle project. Patches a tasks.register("...") { ... } block at the end of the file.
            Abstract Example: ubuild gradle init-task <alias> [name] [group] [description] [body]
            Live Example: ubuild gradle init-task MyProj myTask build "Run my task"
        """.trimIndent(),

        "init-subproject" to """
            init-subproject: Create a new subproject directory, write a starter build.gradle.kts,
            and append include(":<name>") to the project's settings.gradle.kts.
            Abstract Example: ubuild gradle init-subproject <alias> [name] [language]
            Live Example: ubuild gradle init-subproject MyProj server kotlinJvm
        """.trimIndent(),

        "init-project" to """
            init-project: Scaffold a brand-new standalone gradle project (wrapper included) and
            register it under the supplied alias.
            Abstract Example: ubuild gradle init-project <alias> [path] [language]
            Live Example: ubuild gradle init-project MyGame /home/cage/proj/MyGame kotlinJvm
        """.trimIndent(),

        "list-tasks" to """
            list-tasks: Run ./gradlew tasks --all for the project's gradle wrapper and print
            the grouped task list.
            Abstract Example: ubuild gradle list-tasks <alias>
        """.trimIndent(),

        "info" to """
            info: Introspect a gradle project. Shows gradle project properties, system properties,
            contents of any .env / gradle.properties files, and a static source parse of
            build.gradle.kts for val foo: Type by project(...) declarations and System.getenv
            references.
            Abstract Example: ubuild gradle info <alias>
        """.trimIndent(),

        "run" to """
            run: Run the gradle wrapper with the supplied task (or the project's default task).
            Abstract Example: ubuild gradle run <alias> [task]
            Live Example: ubuild gradle run MyProj build
        """.trimIndent(),

        "test" to """
            test: Run the gradle test task for the project.
            Abstract Example: ubuild gradle test <alias>
        """.trimIndent(),

        "clean" to """
            clean: Run the gradle clean task for the project.
            Abstract Example: ubuild gradle clean <alias>
        """.trimIndent()
    )

    println("Gradle Commands")
    println("---------------")
    for((_, text) in helpMap)
    {
        println(text)
        println()
    }
}



/**
 * Print the colossal subcommand help.
 *
 * Called by `ubuild help` and `ubuild colossal help`. Mirrors the format of [printHelp]
 * so the per-command entries are consistent.
 *
 * @since added in v2 alongside the colossal subcommand tree.
 */
fun printColossalHelp()
{
    val helpMap = mapOf(
        "register" to """
            register: Register a colossal project. For colossal-1 this runs the detector against
            the supplied root and writes a ColossalProject with isImplemented=true. For
            colossal-2 the call writes a stub with isImplemented=false and refuses to run.
            Abstract Example: ubuild colossal register [path] [--version colossal-1|colossal-2]
            Live Example: ubuild colossal register /home/cage/Desktop/Workspaces/Autogenesis/Autogenesis
        """.trimIndent(),

        "info" to """
            info: Show colossal-specific info about a registered project: engine version, the
            underlying gradle subproject, and (for colossal-1) the list of detected TPipe
            modules.
            Abstract Example: ubuild colossal info <alias>
        """.trimIndent()
    )

    println("Colossal Commands")
    println("-----------------")
    for((_, text) in helpMap)
    {
        println(text)
        println()
    }
}
