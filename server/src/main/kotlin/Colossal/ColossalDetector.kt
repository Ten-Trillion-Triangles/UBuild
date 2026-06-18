package Colossal

import java.io.File

/**
 * Detect whether a directory tree is a Colossal 1 project (the current TPipe +
 * Autogenesis stack). The detector is intentionally cheap: it just looks for
 * known fingerprints in `settings.gradle.kts` and a few sibling directories.
 *
 * Colossal 2 detection is a no-op: there is no engine to detect yet, and the
 * `register --version colossal-2` command writes a stub without ever calling
 * this detector.
 *
 * @since added in v2.
 */
object ColossalDetector
{
    /**
     * Substrings of `settings.gradle.kts` that, taken together, fingerprint a
     * Colossal 1 (Autogenesis) root. We require at least two of these to be
     * present to call the project colossal-1, to avoid false positives on
     * unrelated gradle projects.
     */
    private val COLOSSAL_1_SETTINGS_MARKERS : List<String> = listOf(
        "accelbyteSdk",
        "kvisionApp",
        "mapEditor",
        "matchmaker",
        "electronApp",
        "electronJukebox",
        "jukebox",
        "audioTracksEditor",
    )


    /**
     * Names of TPipe subproject directories we expect to find as siblings of the
     * Autogenesis root. We accept the project as colossal-1 if at least one of
     * these exists, OR if there is a `TPipe` directory anywhere one level up.
     */
    private val COLOSSAL_1_TPIPE_SIBLINGS : List<String> = listOf(
        "TPipe",
        "TPipe-Bedrock",
        "TPipe-Defaults",
        "TPipe-GenericOpenAI",
        "TPipe-MCP",
        "TPipe-Ollama",
        "TPipe-OpenRouter",
        "TPipe-TraceServer",
        "TPipe-Tuner",
    )


    /**
     * The detected result.
     *
     * @property isColossal1 True if the directory looks like a colossal-1 root.
     * @property matchedSettingsMarkers The list of [COLOSSAL_1_SETTINGS_MARKERS]
     *                                  that were found in `settings.gradle.kts`.
     * @property matchedTpipeSiblings The list of [COLOSSAL_1_TPIPE_SIBLINGS]
     *                                that exist as siblings of [root].
     */
    data class Detection(
        val isColossal1 : Boolean,
        val matchedSettingsMarkers : List<String>,
        val matchedTpipeSiblings : List<String>,
    )


    /**
     * Run the detector against [root].
     *
     * @param root The directory to inspect.
     * @return A [Detection] describing what was found. Never null; the caller
     *         checks [Detection.isColossal1] to decide whether to register.
     */
    fun detect(root : File) : Detection
    {
        if(!root.exists() || !root.isDirectory)
        {
            return Detection(false, emptyList(), emptyList())
        }

        val matchedSettings = matchSettingsMarkers(root)
        val matchedSiblings = matchTpipeSiblings(root)
        val isColossal1 = matchedSettings.size >= 2 && matchedSiblings.isNotEmpty()

        return Detection(
            isColossal1 = isColossal1,
            matchedSettingsMarkers = matchedSettings,
            matchedTpipeSiblings = matchedSiblings,
        )
    }


    private fun matchSettingsMarkers(root : File) : List<String>
    {
        val settingsFile = locateSettingsFile(root) ?: return emptyList()
        val text = settingsFile.readText()
        return COLOSSAL_1_SETTINGS_MARKERS.filter { text.contains(it) }
    }


    private fun matchTpipeSiblings(root : File) : List<String>
    {
        val matched = mutableListOf<String>()
        //First, check immediate siblings (the canonical colossal-1 layout).
        val parent: File? = root.parentFile
        if(parent != null)
        {
            for(name in COLOSSAL_1_TPIPE_SIBLINGS)
            {
                if(File(parent, name).isDirectory)
                {
                    matched.add(name)
                }
            }
        }
        //Then, walk up to two levels looking for a TPipe repo. The user keeps TPipe
        //and Autogenesis in separate top-level workspace folders, so we look for
        //TPipe at the workspace root as well.
        var cursor: File? = parent
        repeat(2) {
            if(cursor != null)
            {
                for(name in COLOSSAL_1_TPIPE_SIBLINGS)
                {
                    val candidate = File(cursor, name)
                    if(candidate.isDirectory && name !in matched)
                    {
                        matched.add(name)
                    }
                }
                cursor = cursor.parentFile
            }
        }
        return matched
    }


    /**
     * Locate the settings file. Prefers the Kotlin DSL (`settings.gradle.kts`),
     * falls back to the Groovy DSL (`settings.gradle`).
     */
    fun locateSettingsFile(root : File) : File?
    {
        val kts = File(root, "settings.gradle.kts")
        if(kts.exists()) return kts
        val groovy = File(root, "settings.gradle")
        if(groovy.exists()) return groovy
        return null
    }
}
