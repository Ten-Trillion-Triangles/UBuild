package Gradle

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the static gradle project introspector. We build a small fake project
 * tree in a temp directory and assert the parser extracts the expected entries.
 *
 * @since added in v2.
 */
class ProjectIntrospectorTest
{
    private lateinit var tempRoot : File


    @BeforeTest
    fun setUp()
    {
        tempRoot = Files.createTempDirectory("gradle-introspector-test").toFile()
    }


    @AfterTest
    fun tearDown()
    {
        tempRoot.deleteRecursively()
    }


    @Test
    fun extractsProjectPropertiesAndEnvVars()
    {
        File(tempRoot, "build.gradle.kts").writeText(
            """
            val appName: String by project("MyApp")
            val port: Int by project("8080")

            fun main() {
                val key = System.getenv("API_KEY")
                val home = System.getenv("HOME")
            }
            """.trimIndent()
        )
        val intro = ProjectIntrospector.introspect(tempRoot)
        assertEquals(2, intro.projectProperties.size)
        val appName = intro.projectProperties.firstOrNull { it.name == "appName" }
        val port = intro.projectProperties.firstOrNull { it.name == "port" }
        assertEquals("String", appName?.type)
        assertEquals("MyApp", appName?.defaultValue)
        assertEquals("Int", port?.type)
        assertEquals("8080", port?.defaultValue)
        assertEquals(listOf("API_KEY", "HOME"), intro.environmentVariables)
    }


    @Test
    fun readsGradlePropertiesAndEnvFiles()
    {
        File(tempRoot, "build.gradle.kts").writeText("") //introspector doesn't care
        File(tempRoot, "gradle.properties").writeText(
            """
            #comment line
            kotlin.code.style=official
            org.gradle.jvmargs=-Xmx2g
            """.trimIndent()
        )
        File(tempRoot, ".env").writeText(
            """
            #env comment
            DATABASE_URL=jdbc:postgres://localhost/mydb
            SECRET=topsecret
            """.trimIndent()
        )
        val intro = ProjectIntrospector.introspect(tempRoot)
        val byKey = intro.envFileEntries.associateBy { it.key }
        assertEquals("official", byKey["kotlin.code.style"]?.value)
        assertEquals("-Xmx2g", byKey["org.gradle.jvmargs"]?.value)
        assertEquals("jdbc:postgres://localhost/mydb", byKey["DATABASE_URL"]?.value)
        assertEquals("topsecret", byKey["SECRET"]?.value)
        assertTrue(intro.envFileEntries.any { it.source == "gradle.properties" })
        assertTrue(intro.envFileEntries.any { it.source == ".env" })
    }


    @Test
    fun emptyProjectProducesEmptyIntrospection()
    {
        val intro = ProjectIntrospector.introspect(tempRoot)
        assertEquals(emptyList(), intro.projectProperties)
        assertEquals(emptyList(), intro.environmentVariables)
        assertEquals(emptyList(), intro.envFileEntries)
    }


    @Test
    fun toleratesGroovyBuildFiles()
    {
        File(tempRoot, "build.gradle").writeText(
            """
            def apiKey = System.getenv("GROOVY_KEY")
            """.trimIndent()
        )
        val intro = ProjectIntrospector.introspect(tempRoot)
        assertEquals(listOf("GROOVY_KEY"), intro.environmentVariables)
    }
}
