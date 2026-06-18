package Globals

import Config.ConfigFile
import Config.Engine
import Config.Migration
import Util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Singleton that houses all the loaded config data for ubuild. The config file is loaded as json upon startup,
 * then is deserialized to data classes. This singleton locks down access to make tracking who is reading and writing
 * to it easier.
 */
object env {

    //Global config file that is currently loaded. Is private to make it easier to track who is writing to it.
    private var config = ConfigFile()

    //Command line arguments
    private var args = mutableListOf<String>()

    /**
     * Load the ubuild config file. Will create and serialize a brand-new config file if one doesn't exist.
     */
    fun loadConfig() : ConfigFile
    {
        val homeFolder = getHomeFolder()

        //Without finding the user's  home (or documents folder if windows) we cannot proceed.
        if(!homeFolder.exists() && !homeFolder.isDirectory)
        {
            throw Exception ("Home folder does not exist. Unable to load config file @ loadConfig in Globals.kt")
        }

        val configPath = "$homeFolder/.ubuild/config.json"
        var fileString = ""

        if(File(configPath).exists())
        {
            fileString = readStringFromFile(configPath)
        }


        //If file string is empty we need to just create a new default one.
        if(fileString.isEmpty())
        {
            config = ConfigFile()
            generateBuildFlagDefaults() //Generate default build flags if this is a fresh config file.
            serialize(config, File(configPath))
            return config
        }

        config = Config.Migration.migrateIfNeeded(File(configPath))
        return config.copy() //todo: copy() is often too shallow to return nested contents. Possible bug?
    }


    /**
     * Save the global config object back to disk.
     */
    fun saveConfig()
    {
        val homeFolder = getHomeFolder()
        if(!homeFolder.exists() && !homeFolder.isDirectory)
        {
            throw Exception ("Home folder does not exist. Unable to save config file @ saveConfig in Globals.kt")
        }

        serialize(config, File("${getHomeFolder()}/.ubuild/config.json"))
        loadConfig() //Always reload so any update calls that call this will have the config fresh.
    }

    /**
     * Update the ubuild config file. This function exists to create friction when trying to write to configFile data class.
     * By doing so we can better track who and what is writing to this global var, and require the coder in question to
     * commit to doing so in a strict way.
     */
    fun updateConfigFile(newConfig : ConfigFile)
    {
        config = newConfig
        saveConfig()
    }

    /**
     * Access function to get at the current ubuild config file
     */
    fun getConfig() : ConfigFile
    {
        val configString = Json.encodeToString(config)
        return Json.decodeFromString(configString)
    }

    /**
     * Sets the default engine config for the runtime and updates the config file to save the change.
     * This should typically be called by the swap command in the cli wizard mode.
     */
    fun setDefaultEngine(engine : String)
    {
        config.defaultConfig = engine
        config.loadedConfigKey = engine
        saveConfig()
    }

    /**
     * Get the set default engine configuration. This holds what used to be the separate ubuild config file
     * in the prior C++ version of ubuild.
     * @since If the key is not found, this can return null if the key doesn't exist. Be sure to handle that after calling this
     * function.
     * @return The default engine configuration as an Engine data class
     */
    fun getDefaultEngine() : Engine
    {
        val engine = config.engineConfigs.getOrDefault(config.loadedConfigKey, Engine())
        val newEngineString = Json.encodeToString(engine)
        return Json.decodeFromString(newEngineString)
    }

    /**
     * Updates the currently loaded engine configuration. This is what holds project configurations for that
     * engine version. Will save using the currently loaded key in the config file. Which is loaded either at startup,
     * or by the swap command.
     */
    fun updateEngineConfig(newConfig : Engine)
    {
        val currentConfig = config.loadedConfigKey
        config.engineConfigs[currentConfig] = newConfig
        saveConfig()
    }


    /**
     * Access function for command line arguments.
     */
    fun getArgs() : List<String>
    {
        return args.toList()
    }



    //Setter function to store program arguments globally. Prevents user from manipulating the list directly.
    fun setArgs(newArgs : Array<String>)
    {
        args = newArgs.toMutableList()
    }

    fun swapEngineConfig(newConfig : String)
    {
        var targetConfig = newConfig

        if(newConfig == "")
        {
            targetConfig = "default"
        }
        
        config.loadedConfigKey = targetConfig
        saveConfig()
    }

    fun setDefaultEngineConfig(newConfig : String)
    {
        config.defaultConfig = newConfig
        saveConfig()
    }

    fun removeCommandFromArgs()
    {
        args.removeAt(0)
    }

    /**
     * Add or update a flag alias.
     * Flag aliases are a way to quickly store multiple build flags which can be easily appended
     * often automatically to a package string.
     */
    fun setFlagAlias()
    {
        val args = getArgs().toMutableList()
        var alias = ""
        var flags = ""
        if(args.isNotEmpty())
        {
            alias = args[0]
            args.removeAt(0)

            for(i in args)
            {
                flags = "$i "
            }
        }

        else
        {
            println("Enter a flag alias name.")
            alias = readln()
            println("Enter any build flags you wish to add.")
            flags = readln()
        }

        val config = env.getConfig()
        config.flagAliasStrings[alias] = flags
        env.updateConfigFile(config)
        println("Flag alias $alias set to $flags")
    }

    //Get a saved flag alias.
    fun getFlagAlias(alias : String) : String
    {
        val config = env.getConfig()
        return config.flagAliasStrings[alias] ?: ""
    }


    /**
     * Called if we're loading a fresh config file for the very first time.
     * Applies default build flag aliases.
     */
    fun generateBuildFlagDefaults()
    {
        val defaults = mutableMapOf<String, String>()

        defaults["basic"] = "-prereqs -stage -pak -CrashReporter" //Basic build flags commonly used by all builds.
        defaults["empty"] = "" //Empty string if you want no build flags.
        defaults["shipping"] = "-prereqs -stage -pak -CrashReporter -nodebuginfo" //Shipping build flags.
        defaults["android"] = "-prereqs -stage -pak -CrashReporter -UpdateIfNeeded" //Android build flags.
        defaults["android-shipping"] = "-prereqs -stage -pak -CrashReporter -UpdateIfNeeded -nodebuginfo" //Android build flags for shipping.
        config.flagAliasStrings = defaults

    }
}