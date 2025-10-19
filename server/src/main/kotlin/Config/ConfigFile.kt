package Config

/**
 * Main configuration file. Holds top level data class that contains the engine configuration.
 * That data class then holds a mutable map of Project data classes that holds the project configuration for that
 * engine configuration.
 */
@kotlinx.serialization.Serializable
data class ConfigFile(@kotlinx.serialization.Transient var init : Boolean = true)
{
    //Default engine configuration will attempt to load this on startup if not empty.
    var defaultConfig = ""

    @kotlinx.serialization.EncodeDefault
    //Engine configuration that is currently loaded.
    var loadedConfigKey = "default"

    //List of possible engine configurations. Allows ubuild to track multiple engines and project versions.
    var engineConfigs = mutableMapOf<String, Engine>()

    /**List of saved launch strings for the launch command
     * This is saved globally because there's no benefit to concealing sandboxed configs like there is
     * for separate engine versions.
     */
    var launchStrings = mutableMapOf<String, String>()

    /**
     * List of saved extra flags that can be passed on the command line.
     */
    var flagAliasStrings = mutableMapOf<String, String>()
}
