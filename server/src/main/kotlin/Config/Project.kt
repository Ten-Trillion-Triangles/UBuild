package Config

/**
 * Holds project level configurations. Is stored as a mutable map inside
 * an Engine data class.
 */
@kotlinx.serialization.Serializable
data class Project(@kotlinx.serialization.Transient val init : Boolean = true)
{
    var projectName = "" // Name of the ubuild project alias.
    var projectRoot = "" // Root folder of the project.
    var projectTarget = "" //Name of the unreal engine project. Not to be confused with platform targets like Editor, Game, etc.
    var uprojectPath = "" // Full path to the uproject file.
    var archivePath = "" // Path where we want packaged games to be dumped to.

    var generatePathString = "" //Full path string required to generate the project using GenerateProjectFiles.sh
    var switchVersionPathString = "" //Full path string required to switch version using UnrealVersionSelector.

    var defaultFlagAlias = "basic" //Default UAT flag alias.
    var defaultTarget = "" //Default build target. Is applied only if the import command is used.
    var defaultPlatform =  "" //Default platform. Is applied only if the import command is used.
    var defaultConfig = "" //Default project package configuration. Is applied only if the import command is used.


    /**
     * List of build strings for each possible target followed by engine configuration.
     * Key is the target, data class is another map holding configuration plus build string.
     */
    var buildStringList = mutableMapOf<String, BuildString>()

    /**
     * List of package strings for each possible target followed by engine configuration.
     * Key is the target, data class is another map holding configuration plus build string.
     */
    var packageList = mutableMapOf<String, BuildString>()


    //List of possible build target configurations. Read only and here for convenience.
    val targetList = listOf("", "Editor", "Server", "Game", "Client")


    //List of possible build configurations. Read only and here for convenience.
    val configList = listOf("Development", "DevelopmentEditor", "Shipping", "Test", "Debug", "DebugGame")


    //List of possible platforms supported by unreal engine. Read only and here for convenience.
    val platformList = listOf("Win64", "Linux", "Android", "IOS", "Mac")


}
