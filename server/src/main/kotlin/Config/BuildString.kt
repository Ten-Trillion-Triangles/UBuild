package Config


/**
 * Data class meant to help wrap the complexity of needing to map build targets. (IE: CCGToolkit, CCGToolkitServer)
 * to each build configuration that target can be built in.
 */
@kotlinx.serialization.Serializable
data class BuildString(var init : Boolean = true)
{
    /**
     * Keys are build configurations such as Development, Debug, DebugGame etc.
     * Values are another nested data class of BuildString which holds the platform as key and package string as value
     */
    var buildMap = mutableMapOf<String, BuildString>()

    //End of the recursive data class. Holds the platform as key and package string as value
    var innerMap = mutableMapOf<String, String>()
}
