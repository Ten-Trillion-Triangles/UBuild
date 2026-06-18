package Config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * v1 -> v2 config migration.
 *
 * The v1 on-disk format stored every project as a flat [Project] data class with no
 * `type` discriminator. The v2 format stores each project as a sealed [Project] subtype
 * (currently [UnrealProject] / [GradleProject] / [ColossalProject]) and the [ConfigFile]
 * declares a `configVersion: Int` field.
 *
 * The migration is intentionally one-way and idempotent. It is called from
 * [Globals.env.loadConfig] before the deserialized [ConfigFile] is returned to the rest
 * of the app. If anything goes wrong we leave the original file untouched and surface
 * the error to the user.
 *
 * The on-disk rewrite is done at the [kotlinx.serialization.json.JsonObject] level so we
 * can add the `type` discriminator to each project without needing a parallel v1
 * mirror data class.
 *
 * @since added in v2 to support the sealed [Project] hierarchy.
 */
object Migration
{
    /** Magic number for "this file predates the schema versioning." */
    const val V1: Int = 1

    /** Magic number for "this file uses the sealed [Project] hierarchy." */
    const val V2: Int = 2


    /**
     * Read [source] as raw text, attempt to interpret it as a [ConfigFile], and if its
     * `configVersion` is missing or [V1] rewrite it in the v2 form.
     *
     * @param source The on-disk config file. Must exist; callers should check first.
     * @return The v2 [ConfigFile] ready to be returned from [Globals.env.loadConfig].
     *         If the file is already v2 this is effectively a no-op.
     * @throws IllegalStateException if the file cannot be parsed as JSON at all.
     * @throws kotlinx.serialization.SerializationException if the JSON is well-formed
     *         but cannot be deserialized into a v1 or v2 [ConfigFile].
     */
    fun migrateIfNeeded(source : File) : ConfigFile
    {
        val rawText = source.readText()
        val json = v2Json()

        //Parse the file as a raw JsonObject first so we can detect the version without
        //committing to the v2 schema. The v2 schema requires every project to have a
        //`type` discriminator; v1 projects do not, so the v2 decoder would fail.
        val rawElement = runCatching { json.parseToJsonElement(rawText) }
            .getOrElse { parseError ->
                throw IllegalStateException(
                    "Failed to read ubuild config at ${source.absolutePath}. " +
                    "The file exists but is not valid UBuild config JSON.",
                    parseError
                )
            }
        val rawObject = rawElement as? JsonObject
            ?: throw IllegalStateException("UBuild config root must be a JSON object.")
        val declaredVersion = (rawObject["configVersion"] as? JsonPrimitive)?.content?.toIntOrNull() ?: V1

        if(declaredVersion >= V2)
        {
            //Already v2. Decode and return.
            return json.decodeFromString<ConfigFile>(rawText)
        }

        //v1 -> v2: back up the original, then rewrite the JSON to add the type
        //discriminator and the configVersion field. After the rewrite, decode again as
        //v2 to get a properly typed in-memory model.
        val backupPath = File(source.parentFile, "${source.name}.v${V1}.bak")
        source.copyTo(backupPath, overwrite = true)

        val upgraded = upgradeToV2(rawObject)
        val upgradedJson = json.encodeToString(JsonObject.serializer(), upgraded)
        source.writeText(upgradedJson)
        return json.decodeFromString<ConfigFile>(upgradedJson)
    }


    /**
     * Walk the raw JSON tree and add a `type: "unreal"` discriminator to every project,
     * plus a `configVersion: 2` field at the top.
     *
     * @param root The parsed JSON tree of the on-disk config file.
     * @return A new [JsonObject] that decodes cleanly into the v2 [ConfigFile] shape.
     */
    private fun upgradeToV2(root : JsonObject) : JsonObject
    {
        val upgradedEngines = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for((engineName, engineNode) in root["engineConfigs"] as? JsonObject
            ?: throw IllegalStateException("engineConfigs must be a JSON object."))
        {
            val engineObj = engineNode as? JsonObject
                ?: throw IllegalStateException("engineConfigs[$engineName] must be a JSON object.")
            val projectsObj = engineObj["projects"] as? JsonObject
                ?: throw IllegalStateException("engineConfigs[$engineName].projects must be a JSON object.")

            val upgradedProjects = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
            for((projectName, projectNode) in projectsObj)
            {
                val projectObj = projectNode as? JsonObject
                    ?: throw IllegalStateException("project $projectName must be a JSON object.")

                //Add the `type` discriminator so kotlinx.serialization can route the
                //project to the [UnrealProject] subclass.
                upgradedProjects[projectName] = buildJsonObject {
                    put("type", JsonPrimitive("unreal"))
                    for((k, v) in projectObj)
                    {
                        put(k, v)
                    }
                }
            }

            upgradedEngines[engineName] = buildJsonObject {
                for((k, v) in engineObj)
                {
                    if(k == "projects")
                    {
                        put("projects", JsonObject(upgradedProjects))
                    }
                    else
                    {
                        put(k, v)
                    }
                }
            }
        }

        return buildJsonObject {
            put("configVersion", JsonPrimitive(V2))
            for((k, v) in root)
            {
                if(k == "engineConfigs")
                {
                    put("engineConfigs", JsonObject(upgradedEngines))
                }
                else
                {
                    put(k, v)
                }
            }
        }
    }


    /**
     * The [Json] configuration used for both reading and writing config files. Kept
     * lenient and tolerant of unknown keys so the migration can round-trip files that
     * were written by a future UBuild version without immediately failing.
     */
    private fun v2Json() : Json = Json {
        prettyPrint = true
        prettyPrintIndent = " "
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}
