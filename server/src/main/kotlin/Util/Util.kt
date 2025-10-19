package Util

import Config.BuildString
import Config.ConfigFile
import Config.Engine
import Config.Project
import Enums.LineEnding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException


/**
 * Returns the user's home folder. Or the document folder on Windows.
 * This function expects that standard naming conventions are used.
 * Bizzare non-standard naming conventions are not supported, and it's the user's fault
 * if they do not comply.
 */
fun getHomeFolder(): File {
    val os = System.getProperty("os.name")
    return if (os.contains("Windows")) {
        File(System.getenv("USERPROFILE"))
    } else {
        File(System.getProperty("user.home"))
    }
}


/**
 * Copy file in one directory to another. Only unix is supported.
 * @param starPath The path of the file to copy.
 * @param destPath The path to copy the file to.
 */
fun copyFile(starPath : String, destPath : String) {

    try {
        val targetFile = File(destPath)
        val sourceFile = File(starPath)
        sourceFile.copyTo(targetFile, overwrite = false)
    } catch (e: NoSuchFileException) {
        println("Error: Source file not found: ${e.message}")
    } catch (e: FileAlreadyExistsException) {
        println("Error: Destination file already exists: ${e.message}")
    } catch (e: FileSystemException) {
        println("Error: Failed to create target directory: ${e.message}")
    } catch (e: IOException) {
        println("Error: I/O error occurred: ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
    }

}



/**
 * Copy directory in one directory to another. Only unix is supported.
 * @param starPath The path of the directory to copy.
 * @param destPath The path to copy the directory to.
 */
fun copyDir(starPath : String, destPath : String) {
    //File(starPath).copyRecursively(File(destPath), true)

    File(starPath).copyRecursively(
        File(destPath),
        overwrite = true,
        onError = { file, exception ->
            when (exception) {
                is AccessDeniedException -> {
                    println("Error copying $file: Permission denied")
                    OnErrorAction.SKIP
                }
                is IOException -> {
                    println("Error copying $file: I/O error: ${exception.message}")
                    OnErrorAction.SKIP // Or consider retrying with a delay
                }
                is SecurityException -> {
                    println("Error copying $file: Security violation: ${exception.message}")
                    OnErrorAction.SKIP // Or log and skip, depending on the severity
                }
                else -> {
                    println("Unexpected error copying $file: ${exception.message}")
                    OnErrorAction.SKIP // Log the error and terminate to prevent further issues
                }
            }
        }
    )
}



fun deleteDir(path : String)
{
    try
    {
        File(path).deleteRecursively()
    }
    catch (e : AccessDeniedException)
    {
        println("Error deleting $path: Permission denied")
        OnErrorAction.SKIP
    }
    catch (e : IOException)
    {
        println("Error deleting $path: I/O error: ${e.message}")
        OnErrorAction.SKIP
    }
    catch (e : SecurityException)
    {
        println("Error deleting $path: Security violation: ${e.message}")
        OnErrorAction.SKIP
    }
    catch (e : Exception)
    {
        println("Unexpected error deleting $path: ${e.message}")
        OnErrorAction.SKIP
    }

}


/**
 * Execute a bash command.
 * @param command The command to execute. Does not pipe buffer output. Will stall the thread until
 * the command has finished.
 * @return The exit value of the command.
 */
fun executeBashCommand(command : String) : Int
{
    val process = ProcessBuilder(*command.split("\\s+".toRegex()).toTypedArray())
        .inheritIO()
        .start()
    process.waitFor()
    return process.exitValue()
}


/**
 * Find a file cascading up the directory tree. This is required because we don't know where the program's working dir
 * is compared to where our target file might be up above. This can even vary from running as jar, or as gradlew, or even
 * as a docker container.
 * @param path The path to start from.
 * @return The file found.
 */
fun findFileCascading(path : String) : File
{
    var mutablePath = path
    val maxIterations = 10
    var iterations = 0

    while (!File(mutablePath).exists())
    {
        mutablePath = "../$mutablePath"

        if(iterations > maxIterations)
        {
            return File("") //Return empty if we exceed the max limit.
        }

        iterations++
    }

    return File(mutablePath)
}


//Get the program's working directory
fun getWorkingDirectory() : String
{
    return File(".").absolutePath.removeSuffix(".")
}


/**
 * Write a string to a file with a Unix filepath.
 *
 * @param filepath The Unix filepath to write to.
 * @param content The string to write to the file.
 */
fun writeStringToFile(filepath: String, content: String) {

    if(!File(filepath).exists())
    {
        val file = File(filepath)
        val split = filepath.split("/").toMutableList()
        split.removeAt(split.lastIndex)
        var folderPath = ""

        for(i in split)
        {
            folderPath = "$folderPath$i/"
        }

        //Remove extra trailing / on the path.
        folderPath = folderPath.removeSuffix("/")
        val dir = File(folderPath)

        if(!dir.exists())
        {
            val result = dir.mkdirs()
            if(!result)
            {
                throw error("Unable to load config file because we can't create the main directory to store it in" +
                        " in writeStringToFile @ Util.kt")
                return
            }
        }


    }
    File(filepath).writeText(content)
}



/**
 * Read a string from a file with a Unix filepath.
 *
 * @param filepath The Unix filepath to read from.
 *
 * @return The string read from the file.
 */
fun readStringFromFile(filepath: String): String {
    return File(filepath).readText()
}


/**
 * Serialize any data class to a json file.
 * @param obj The object to serialize.
 * @param filePath The path to the file to write to.
 */
inline fun <reified T> serialize(obj : T, filePath : File)
{
    val config = obj as ConfigFile

    val json = Json{ // this returns the JsonBuilder
    prettyPrint = true
    // optional: specify indent
    prettyPrintIndent = " " }


    val output = json.encodeToString(config)
    writeStringToFile(filePath.absolutePath, output)
}


/**
 * Decode a json file into a data class.
 * @param filePath The path to the file to read from.
 * @return The decoded data class. May require casting afterward.
 */
inline fun <reified T> deserialize(filePath : File) : T
{
    return Json.decodeFromString<T>(readStringFromFile(filePath.absolutePath))
}


/**
 * Launch a program with a given path and arguments.
 * All inputs and outputs will be redirected.
 * The new program will block until it finishes.
 * @param path The path to the program to launch.
 * @param args The arguments to pass to the program.
 */
fun launchProgram(path : MutableList<String>, retryCount : Int = 0)
{
    var retryAttempts = retryCount

    //Launch the program and block our process until it finishes.
    val builder = ProcessBuilder(path)
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    builder.redirectError(ProcessBuilder.Redirect.INHERIT)
    builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
    //println(builder.command())
    val process = builder.start()
    process.waitFor()

    //Allow for retries to bypass stubborn ispc bugs introduced in 5.5 and beyond.
    if(process.exitValue() != 0 && retryAttempts > 0)
    {
        launchProgram(path, retryCount - 1)
    }
}


/**
 * Get the line ending of a string.
 * @param fileString The string to get the line ending from.
 * @return The line ending of the string.
 */
fun getLineEnding(fileString : String) : LineEnding
{
    if(fileString.contains("\r\n"))
    {
        return LineEnding.Windows
    }
    else if(fileString.contains("\n"))
    {
        return LineEnding.Unix
    }
    else if(fileString.contains("\r"))
    {
        return LineEnding.Mac
    }
    else
    {
       return LineEnding.Unknown
    }
}


fun convertLineEnding(filePath: File) : Boolean
{
    val fileString = filePath.readText()
    val lineEnding = getLineEnding(fileString)

    if(lineEnding == LineEnding.Unknown)
    {
        println("Unknown line ending found @ convertLineEnding in Util.kt")
        return false
    }

    if(lineEnding == LineEnding.Mac)
    {
        filePath.writeText(fileString.replace("\r", "\n"))
        println("Converting mac line endings in $filePath")
        return true
    }

    return true //Valid if unix or windows already.
}

/**
 * Return a list of directories in a given directory recursively.
 * @param root The root directory to start from.
 * @return A list of directories in the root directory.
 */
fun getDirRecursive(root : File) : List<File>
{
    return root.walk().toList()
}


/**
 * Return the os that unreal editor is running on.
 * @since This is only used for the editor. Package platforms such as ios and android are not supported.
 * when setting up build strings for those platforms you need to type them directly into the string formatter.
 */
fun getOs() : String
{
    val os = System.getProperty("os.name").lowercase()
    return when (os) {
        "linux" -> "Linux"
        "mac os x" -> "Mac"
        else -> "Win64"
    }
}


/**
 * Splits a path string stored in the config file back into two separate strings.
 * @return A map containing the path and arguments. The key is the path to the program and the value is the arguments.
 * This is required because ProcessBuilder does not support passing arguments as a single string.
 */
fun splitProgramString(programString: String): MutableList<String>
{
    val first = programString.split(Regex("(?<!\\\\)\\s+")).toSet().toCollection(ArrayList()).toMutableList()
    val newList = mutableListOf<String>()
    for(i in first)
    {
        newList.add(i)
    }

    return newList

}


/**
 * Generates the strings required to pass into UAT to package projects.
 * @param project The project to package. This is a reference and will be updated as such.
 * @param engine The engine to package. This is a reference and will be updated as such.
 * @return The params are modified by reference so there's no return value here.
 */
fun generatePackageString(project : Project, engine : Engine)
{
    /**
     * Loop through each project target. Then loop through each build configuration and generate package strings for each.
     * These will be used to invoke UAT and package projects.
     */
    for(target in project.targetList)
    {
        //Target EX: CCGToolkit, CCGToolkitServer, CCGToolkitEditor etc.
        val projectTarget = project.projectTarget+target


        /**
         * Holds the value associated with the key stored inside outerBuildString's map.
         * This key would be stored as the build configuration such as Development, Shipping, DevelopmentEditor etc.
         */
        val configBuildString = BuildString()


        for (buildConfig in project.configList)
        {

            /**
             * Final data class that will hold platforms as the key and package strings as the value.
             * Must be declared before it's loop to prevent it from expiring at the end of the loop.
             */
            val platformBuildString = BuildString()

            for(platform in project.platformList)
            {
                val packageString = "${engine.uat} BuildCookRun -project=${project.uprojectPath} " +
                        "-ScriptsForProject=${project.uprojectPath} -noP4 " +
                        "-Platform=$platform -clientconfig=$buildConfig -serverconfig=$buildConfig -cook -allmaps -build -stage " +
                        "-archive -target=$projectTarget -archivedirectory=${project.archivePath}"


                //Add the package string to the platformBuildString with the key being the platform/OS.
                platformBuildString.innerMap[platform] = packageString

            }

            /**
             * At the end of the platform loop, add the platformBuildString to the configBuildString
             * with the key being the build configuration.
             */
            configBuildString.buildMap[buildConfig] = platformBuildString

        }

        /**
         * Add the target configBuildString to the outerBuildString with the key being the target.
         * This is the final step of constructing build configs backwards.
         */
        project.packageList[projectTarget] = configBuildString

    }

    /**
     * Fully update the project and engine data classes. These were passed by reference so the caller will not
     * need this to be returned outside the function.
     */
    engine.projects[project.projectName] = project
}


/**
 * Generates build strings for building the project. This is equal to building from an IDE.
 * Only the operating system/platform your building from is supported.
 * @param project The project to build. This is a reference and will be updated as such.
 * @param engine The engine to build. This is a reference and will be updated as such.
 * @return The params are modified by reference so there's no return value here.
 */
fun generateBuildStrings(project : Project, engine : Engine)
{
    val targets = project.targetList
    val configs = project.configList


    /**
     * Loop through each project target EX: CCGToolkit, CCGToolkitServer, CCGToolkitEditor etc.
     * Then generate a build string for each build configuration.
     */
    for(target in targets)
    {
        //Second layer of build string objects. Splits map by config then build string.
        val innerBuildString = BuildString()


        for(config in configs)
        {
            /**
             * @note A compiler build flag is also required. But is supplied by the "build" command
             * so it was left out here.
             */

            val bulidString = "${engine.buildSh} ${project.projectTarget}$target ${getOs()} $config -Project=${project.uprojectPath} -buildscw"


            innerBuildString.innerMap[config] = bulidString
        }

        //Update top level build string.
        project.buildStringList[target] = innerBuildString

    }

}


/**
 * Used to parse through any remaining arguments. Gather all of them and then push them back as a string.
 * This is useful specifically for extra build flags you want to pass into UAT.
 * @param args The list of arguments to parse through.
 * @param index The index to start at.
 */
fun getStringFromArgsbyIndex(args : List<String>, index : Int) : String
{
    var output = ""

    for(i in index until args.size)
    {
        output = "$output ${args[i]}"
    }

    return output
}


fun clearScreen()
{
    when (System.getProperty("os.name")) {
        "Linux", "Mac OS X" -> Runtime.getRuntime().exec("clear").waitFor()
        "Windows" -> Runtime.getRuntime().exec("cls").waitFor()
        else -> println("Unsupported operating system")
    }
}



