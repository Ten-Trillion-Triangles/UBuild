package Config

import kotlinx.serialization.SerialName

/**
 * Project configuration for an Unreal Engine project. This is the most common project type
 * and the original use case for UBuild.
 *
 * We carry every field the v1 flat [Config.Project] data class had, verbatim, so the v1 -> v2
 * config migration can rewrite the on-disk JSON with zero data loss. The build/package string
 * trees are intentionally kept as [Config.BuildString] maps of the same shape they had before;
 * changing them would break the [Parser.generateBuildStrings] / [Parser.generatePackageString]
 * logic and there is no reason to.
 *
 * @since added in v2 to support the sealed [Config.Project] hierarchy.
 */
@kotlinx.serialization.Serializable
@SerialName("unreal")
data class UnrealProject(
    @kotlinx.serialization.Transient val init: Boolean = true,
)
    : Project
{
    //Name of the ubuild project alias.
    override var projectName: String = ""

    //Root folder of the project.
    override var projectRoot: String = ""

    //Path where we want packaged games to be dumped to.
    override var archivePath: String = ""

    //Name of the unreal engine project. Not to be confused with platform targets like Editor, Game, etc.
    var projectTarget: String = ""

    //Full path to the uproject file.
    var uprojectPath: String = ""

    //Full path string required to generate the project using GenerateProjectFiles.sh
    var generatePathString: String = ""

    //Full path string required to switch version using UnrealVersionSelector.
    var switchVersionPathString: String = ""

    //Default UAT flag alias.
    var defaultFlagAlias: String = "basic"

    //Default build target. Is applied only if the import command is used.
    var defaultTarget: String = ""

    //Default platform. Is applied only if the import command is used.
    var defaultPlatform: String = ""

    //Default project package configuration. Is applied only if the import command is used.
    var defaultConfig: String = ""

    /**
     * List of build strings for each possible target followed by engine configuration.
     * Key is the target, data class is another map holding configuration plus build string.
     */
    var buildStringList: MutableMap<String, BuildString> = mutableMapOf()

    /**
     * List of package strings for each possible target followed by engine configuration.
     * Key is the target, data class is another map holding configuration plus package string.
     */
    var packageList: MutableMap<String, BuildString> = mutableMapOf()


    //List of possible build target configurations. Read only and here for convenience.
    val targetList: List<String> = listOf("", "Editor", "Server", "Game", "Client")


    //List of possible build configurations. Read only and here for convenience.
    val configList: List<String> = listOf(
        "Development",
        "DevelopmentEditor",
        "Shipping",
        "Test",
        "Debug",
        "DebugGame"
    )


    //List of possible platforms supported by unreal engine. Read only and here for convenience.
    val platformList: List<String> = listOf("Win64", "Linux", "Android", "IOS", "Mac")
}
