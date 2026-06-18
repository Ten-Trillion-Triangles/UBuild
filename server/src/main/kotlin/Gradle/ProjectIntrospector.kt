package Gradle

import java.io.File

/**
 * Static introspection of a gradle project's `build.gradle.kts` and surrounding
 * environment. We don't shell out to gradle here (the gradle wrapper invocation is
 * expensive and noisy); we read the project files directly and parse them with
 * regexes that cover the common cases.
 *
 * Three groups of data are returned:
 *  - **Project properties**: parsed from `val <name>: <Type> by project(...)` lines
 *    in `build.gradle.kts`.
 *  - **Environment variables**: parsed from `System.getenv("...")` references in
 *    `build.gradle.kts`.
 *  - **Env file entries**: parsed from `gradle.properties` and any `.env` files in
 *    the project root. Each `KEY=value` line becomes one entry.
 *
 * The regexes are intentionally tolerant of formatting: leading whitespace, the
 * presence or absence of `val`/`fun`, and Kotlin-idiomatic spacing are all
 * accommodated. The parser does not attempt to handle the full Kotlin grammar.
 *
 * @since added in v2.
 */
object ProjectIntrospector
{
    /**
     * Aggregated introspection result for a single gradle project.
     *
     * @property projectProperties The list of `val <name>: <Type> by project(...)`
     *                             declarations found in `build.gradle.kts`. Each
     *                             entry has a name, type, and a "default" string
     *                             (the literal value passed to `project(...)`).
     * @property environmentVariables The list of `System.getenv("NAME")` references
     *                                found in `build.gradle.kts`.
     * @property envFileEntries The list of `KEY=value` entries from `gradle.properties`
     *                          and any `.env` files in the project root.
     */
    data class Introspection(
        val projectProperties : List<ProjectProperty> = emptyList(),
        val environmentVariables : List<String> = emptyList(),
        val envFileEntries : List<EnvEntry> = emptyList(),
    )


    /**
     * A single `val foo: Type by project(...)` declaration parsed from
     * `build.gradle.kts`.
     */
    data class ProjectProperty(
        val name : String,
        val type : String,
        val defaultValue : String,
    )


    /**
     * A single `KEY=value` entry parsed from an env file.
     */
    data class EnvEntry(
        val key : String,
        val value : String,
        val source : String,
    )


    //Regex notes:
    //  PROJECT_PROPERTY_RE matches `val name: Type by project("default")` and tolerates
    //    extra whitespace and any quoted default. We capture the name, type, and
    //    default into three groups.
    //  GETENV_RE matches `System.getenv("NAME")` and tolerates the same.
    //  ENV_LINE_RE matches `KEY=value` lines in env files. We anchor at line start
    //    to avoid swallowing the value side of an accidental `=` inside a comment.
    private val PROJECT_PROPERTY_RE : Regex =
        Regex("""val\s+(\w+)\s*:\s*([\w.<>?,\s]+?)\s+by\s+project\(\s*"([^"]*)"\s*\)""")

    private val GETENV_RE : Regex =
        Regex("""System\.getenv\(\s*"([^"]+)"\s*\)""")

    private val ENV_LINE_RE : Regex =
        Regex("""^\s*([A-Za-z_.][A-Za-z0-9_.]*)\s*=\s*(.*?)\s*$""")


    /**
     * Run the static introspector against a gradle project root.
     *
     * @param projectRoot The root directory of the gradle project.
     * @return The aggregated [Introspection]. Empty fields are returned if the
     *         project has no `build.gradle.kts` or env files; the call never throws.
     */
    fun introspect(projectRoot : File) : Introspection
    {
        val buildFile = locateBuildFile(projectRoot)
        val buildText = if(buildFile != null && buildFile.exists())
        {
            buildFile.readText()
        }
        else
        {
            ""
        }

        val properties = if(buildText.isNotEmpty())
        {
            PROJECT_PROPERTY_RE.findAll(buildText).map { match ->
                ProjectProperty(
                    name = match.groupValues[1],
                    type = match.groupValues[2].trim(),
                    defaultValue = match.groupValues[3],
                )
            }.toList()
        }
        else
        {
            emptyList()
        }

        val envVars = if(buildText.isNotEmpty())
        {
            GETENV_RE.findAll(buildText).map { it.groupValues[1] }.distinct().toList()
        }
        else
        {
            emptyList()
        }

        val envEntries = mutableListOf<EnvEntry>()
        for(envFile in locateEnvFiles(projectRoot))
        {
            for(line in envFile.readLines())
            {
                //Skip comments and blank lines.
                if(line.isBlank() || line.trim().startsWith("#"))
                {
                    continue
                }
                val match = ENV_LINE_RE.matchEntire(line) ?: continue
                envEntries.add(
                    EnvEntry(
                        key = match.groupValues[1],
                        value = match.groupValues[2],
                        source = envFile.name,
                    )
                )
            }
        }

        return Introspection(
            projectProperties = properties,
            environmentVariables = envVars,
            envFileEntries = envEntries,
        )
    }


    /**
     * Locate the `build.gradle.kts` (preferred) or `build.gradle` (Groovy fallback)
     * for the project. Returns null if neither exists.
     */
    fun locateBuildFile(projectRoot : File) : File?
    {
        val kts = File(projectRoot, "build.gradle.kts")
        if(kts.exists()) return kts
        val groovy = File(projectRoot, "build.gradle")
        if(groovy.exists()) return groovy
        return null
    }


    /**
     * Locate `gradle.properties` and any `.env` files in the project root.
     * The list is sorted by name for deterministic output.
     */
    fun locateEnvFiles(projectRoot : File) : List<File>
    {
        val results = mutableListOf<File>()
        val gradleProps = File(projectRoot, "gradle.properties")
        if(gradleProps.exists())
        {
            results.add(gradleProps)
        }
        //Look for .env and .env.local variants in the project root only; we don't
        //recursively walk to avoid picking up env files from unrelated subprojects.
        for(name in listOf(".env", ".env.local"))
        {
            val envFile = File(projectRoot, name)
            if(envFile.exists())
            {
                results.add(envFile)
            }
        }
        return results.sortedBy { it.name }
    }
}
