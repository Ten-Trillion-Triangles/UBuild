package Gradle

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScaffoldingAndPatchingTest
{
    @Test
    fun standaloneScaffolderWritesLanguageSpecificProjectFiles()
    {
        val root = Files.createTempDirectory("gradle-scaffolder-test").toFile()
        try
        {
            val expectedPlugin = mapOf(
                SubprojectScaffolder.Language.KOTLIN_JVM to "kotlin(\"jvm\")",
                SubprojectScaffolder.Language.KOTLIN_MULTIPLATFORM to "kotlin(\"multiplatform\")",
                SubprojectScaffolder.Language.JAVA to "plugins {\n    java\n}",
            )

            for((language, plugin) in expectedPlugin)
            {
                val projectDir = File(root, language.name)
                val result = ProjectScaffolder.scaffold(
                    projectDir,
                    "sample-app",
                    "org.example",
                    "1.2.3",
                    language,
                )

                assertEquals(projectDir, result)
                val buildText = File(projectDir, "build.gradle.kts").readText()
                assertTrue(buildText.contains(plugin), "Missing $language plugin in generated build file")
                assertTrue(buildText.contains("group = \"org.example\""))
                assertTrue(buildText.contains("version = \"1.2.3\""))
                assertTrue(File(projectDir, "settings.gradle.kts").readText().contains("rootProject.name = \"sample-app\""))
                assertTrue(File(projectDir, "gradle.properties").readText().contains("kotlin.code.style=official"))
                assertTrue(File(projectDir, "gradle/wrapper/gradle-wrapper.properties").readText().contains("gradle-8.14.4-bin.zip"))
            }
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun scaffoldsKotlinJvmSubprojectAndRegistersItExactlyOnce() =
        assertSubprojectLanguage(
            SubprojectScaffolder.Language.KOTLIN_JVM,
            "kotlin(\"jvm\")",
        )


    @Test
    fun scaffoldsKotlinMultiplatformSubprojectAndRegistersItExactlyOnce() =
        assertSubprojectLanguage(
            SubprojectScaffolder.Language.KOTLIN_MULTIPLATFORM,
            "kotlin(\"multiplatform\")",
        )


    @Test
    fun scaffoldsJavaSubprojectAndRegistersItExactlyOnce() =
        assertSubprojectLanguage(
            SubprojectScaffolder.Language.JAVA,
            "plugins {\n    java\n}",
        )


    @Test
    fun subprojectScaffolderUsesGroovySettingsAndRejectsInvalidOrExistingNames()
    {
        val root = Files.createTempDirectory("gradle-subproject-validation").toFile()
        try
        {
            File(root, "settings.gradle").writeText("rootProject.name = 'parent'\n")
            assertTrue(SubprojectScaffolder.locateSettingsFile(root)?.name == "settings.gradle")
            SubprojectScaffolder.appendInclude(root, "shared-lib")
            assertTrue(File(root, "settings.gradle").readText().contains("include(\":shared-lib\")"))

            assertFailsWith<IllegalArgumentException> {
                SubprojectScaffolder.scaffold(root, "bad/name", SubprojectScaffolder.Language.JAVA)
            }
            File(root, "shared-lib").mkdirs()
            assertFailsWith<IllegalArgumentException> {
                SubprojectScaffolder.scaffold(root, "shared-lib", SubprojectScaffolder.Language.JAVA)
            }
            assertFalse(File(root, "bad/name").exists())
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    @Test
    fun taskPatcherAppendsAndInsertsAtAnAnchorWithoutChangingSourceFile()
    {
        val root = Files.createTempDirectory("gradle-patcher-test").toFile()
        try
        {
            val buildFile = File(root, "build.gradle.kts")
            val original = "plugins {\n    java\n}\n\ntasks.test {\n    useJUnitPlatform()\n}\n"
            buildFile.writeText(original)
            val rendered = BuildFilePatcher.renderTaskBlock(
                taskName = "verifyAssets",
                group = "verification",
                description = "Checks generated assets.",
                dependsOn = listOf("processResources", "test"),
                body = "checkAssets()\nprintln(\"done\")",
            )

            val appended = BuildFilePatcher.applyBlock(buildFile, rendered)
            assertTrue(appended.startsWith(original.trimEnd() + "\n"))
            assertTrue(appended.contains("tasks.register(\"verifyAssets\")"))
            assertTrue(appended.contains("dependsOn(\"processResources\", \"test\")"))
            assertTrue(appended.contains("        checkAssets()\n        println(\"done\")"))
            assertEquals(original, buildFile.readText(), "applyBlock must return updated contents without writing the source file")

            val anchored = BuildFilePatcher.applyBlock(buildFile, "// inserted\n", anchor = "tasks.test")
            assertTrue(anchored.indexOf("// inserted") < anchored.indexOf("tasks.test"))
            val missingAnchor = BuildFilePatcher.applyBlock(buildFile, "// fallback\n", anchor = "missing-anchor")
            assertTrue(missingAnchor.endsWith("// fallback\n"))
        }
        finally
        {
            root.deleteRecursively()
        }
    }


    private fun assertSubprojectLanguage(
        language : SubprojectScaffolder.Language,
        expectedPlugin : String,
    )
    {
        val root = Files.createTempDirectory("gradle-subproject-test").toFile()
        try
        {
            val settings = File(root, "settings.gradle.kts")
            settings.writeText("rootProject.name = \"parent\"\n")
            val name = language.name.lowercase()

            val buildFile = SubprojectScaffolder.scaffold(root, name, language)
            assertEquals(File(root, "$name/build.gradle.kts"), buildFile)
            assertTrue(buildFile.readText().contains(expectedPlugin), "Missing $language plugin in generated subproject")
            SubprojectScaffolder.appendInclude(root, name)

            val include = "include(\":$name\")"
            assertEquals(1, settings.readText().windowed(include.length).count { it == include })
        }
        finally
        {
            root.deleteRecursively()
        }
    }
}
