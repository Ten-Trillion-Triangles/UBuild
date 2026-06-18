package Config

/**
 * Main configuration file. Holds top level data class that contains the engine configuration.
 * That data class then holds a mutable map of Project data classes that holds the project configuration for that
 * engine configuration.
 *
 * @since v2: added [configVersion] so the v1 -> v2 migration can detect old on-disk files and
 * rewrite them transparently. Old files that do not declare a version are treated as v1.
 */
@kotlinx.serialization.Serializable
data class ConfigFile(@kotlinx.serialization.Transient var init : Boolean = true)
{
    /**
     * Schema version of the on-disk config. Defaults to 1 so v1 files that lack the field
     * deserialize as v1 and trigger [Config.Migration.migrateV1ToV2]. New files written by
     * UBuild v2+ will encode 2.
     */
    @kotlinx.serialization.EncodeDefault
    var configVersion: Int = 2

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
