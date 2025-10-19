package Config

/**
 * Data class that holds engine configurations for UBuild.
 * Contains engine root path, build.sh path, and UAT path.
 * Also contains a mutable map of Project data classes which holds project configurations
 * for the given engine configuration.
 */
@kotlinx.serialization.Serializable
data class Engine(@kotlinx.serialization.Transient val init: Boolean = true)
{
    var engineRoot = "" //Path to engine root folder.
    var buildSh = "" //Path to build.sh.Required for building projects. May point to build.bat on windows instead.
    var uat = "" //Path to UAT. Required for packaging projects.
    var generatePath = "" //Path to GenerateProjectFiles.sh. Required for generating projects. May point to a .bat file if on windows.
    var versionSelectorPath = "" //Path to version selector. Required for switching engine versions.
    var version = "" //Raw version string. Required for macOS which has been bugged for years.

    //Projects are stored by key to the corresponding project data class.
    var projects = mutableMapOf<String, Project>()
}
