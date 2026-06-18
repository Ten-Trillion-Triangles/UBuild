package Gradle

import java.io.File

/**
 * Create a new subproject inside an existing gradle build and register it in
 * `settings.gradle.kts` via an `include(":name")` line.
 *
 * The scaffolder is intentionally minimal: it writes a starter `build.gradle.kts`
 * for the chosen language, then patches `settings.gradle.kts` to add the new
 * include. The starter build files are templates embedded in the JAR's resources.
 *
 * @since added in v2.
 */
object SubprojectScaffolder
{
    /**
     * Supported starter languages. The string maps to a template file under
     * `resources/templates/gradle/`.
     */
    enum class Language(val templateName : String)
    {
        KOTLIN_JVM("subproject-kotlin-jvm.gradle.kts"),
        KOTLIN_MULTIPLATFORM("subproject-kotlin-multiplatform.gradle.kts"),
        JAVA("subproject-java.gradle.kts"),
    }


    /**
     * @param parentRoot The root of the parent gradle project.
     * @param subprojectName The new subproject name. Must be a valid gradle
     *                       subproject path component (letters, digits, dashes,
     *                       underscores).
     * @param language The starter language to write.
     * @return The [File] pointing at the new subproject's `build.gradle.kts`.
     * @throws IllegalArgumentException if the subproject already exists or the
     *         name is invalid.
     */
    fun scaffold(parentRoot : File, subprojectName : String, language : Language) : File
    {
        validateSubprojectName(subprojectName)

        val subDir = File(parentRoot, subprojectName)
        if(subDir.exists())
        {
            throw IllegalArgumentException(
                "Subproject '$subprojectName' already exists at ${subDir.absolutePath}."
            )
        }
        subDir.mkdirs()

        val buildFile = File(subDir, "build.gradle.kts")
        val template = loadTemplate(language.templateName)
        buildFile.writeText(template)

        appendInclude(parentRoot, subprojectName)

        return buildFile
    }


    /**
     * Add an `include(":name")` line to the parent project's `settings.gradle.kts`
     * (or `settings.gradle` for Groovy builds). If an include for the same name
     * already exists, the call is a no-op.
     */
    fun appendInclude(parentRoot : File, subprojectName : String)
    {
        val settingsFile = locateSettingsFile(parentRoot)
            ?: throw IllegalStateException(
                "Parent project at ${parentRoot.absolutePath} has no settings.gradle.kts."
            )
        val original = settingsFile.readText()
        val includeLine = "include(\":$subprojectName\")"

        //Idempotent: skip if the include is already present.
        if(original.contains(includeLine))
        {
            return
        }

        val newText = original.trimEnd() + "\n" + includeLine + "\n"
        settingsFile.writeText(newText)
    }


    /**
     * Locate the settings file. Prefers the Kotlin DSL (`settings.gradle.kts`),
     * falls back to the Groovy DSL (`settings.gradle`).
     */
    fun locateSettingsFile(parentRoot : File) : File?
    {
        val kts = File(parentRoot, "settings.gradle.kts")
        if(kts.exists()) return kts
        val groovy = File(parentRoot, "settings.gradle")
        if(groovy.exists()) return groovy
        return null
    }


    private fun validateSubprojectName(name : String)
    {
        if(name.isBlank())
        {
            throw IllegalArgumentException("Subproject name cannot be blank.")
        }
        if(!name.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        {
            throw IllegalArgumentException(
                "Subproject name '$name' contains invalid characters. " +
                "Use letters, digits, dashes, and underscores only."
            )
        }
    }


    private fun loadTemplate(resourceName : String) : String
    {
        val stream = SubprojectScaffolder::class.java.classLoader
            .getResourceAsStream("templates/gradle/$resourceName")
            ?: return defaultTemplate()
        return stream.bufferedReader().use { it.readText() }
    }


    private fun defaultTemplate() : String
    {
        //Fallback if a resource template is missing: a minimal valid kotlin-jvm
        //build file. Better than throwing so the scaffolder always succeeds.
        return """
            //Scaffolded by ubuild gradle init-subproject.
            plugins {
                kotlin("jvm")
            }

            dependencies {
                //Add subproject dependencies here.
            }
        """.trimIndent() + "\n"
    }
}
