package Colossal

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the colossal-1 detector. We don't depend on a real Autogenesis checkout;
 * the tests build a fake directory tree that mirrors the fingerprint the detector
 * looks for, and assert the detector classifies it correctly.
 *
 * @since added in v2.
 */
class ColossalDetectorTest
{
    private lateinit var tempRoot : File
    private lateinit var autoGenRoot : File


    @BeforeTest
    fun setUp()
    {
        tempRoot = Files.createTempDirectory("colossal-detector-test").toFile()
        //Layout mirrors the user's workspace:
        //  tempRoot/
        //    Autogenesis/         <- candidate colossal-1 root
        //      settings.gradle.kts
        //    TPipe/                <- TPipe sibling
        autoGenRoot = File(tempRoot, "Autogenesis")
        autoGenRoot.mkdirs()
        File(tempRoot, "TPipe").mkdirs()
    }


    @AfterTest
    fun tearDown()
    {
        tempRoot.deleteRecursively()
    }


    /**
     * A settings file that mentions two of the known colossal-1 markers and a TPipe
     * sibling directory should be detected as colossal-1.
     */
    @Test
    fun detectsColossal1WhenMarkersAndSiblingPresent()
    {
        val settings = File(autoGenRoot, "settings.gradle.kts")
        settings.writeText(
            """
            rootProject.name = "Autogenesis"
            include(":accelbyteSdk")
            include(":kvisionApp")
            include(":mapEditor")
            """.trimIndent()
        )
        val detection = ColossalDetector.detect(autoGenRoot)
        assertTrue(detection.isColossal1,
            "Expected colossal-1 detection. Got: ${detection}")
        assertTrue(detection.matchedSettingsMarkers.contains("accelbyteSdk"))
        assertTrue(detection.matchedSettingsMarkers.contains("kvisionApp"))
        assertTrue(detection.matchedTpipeSiblings.contains("TPipe"))
    }


    /**
     * A gradle project that has none of the colossal-1 markers should not be
     * detected as colossal-1, even if a TPipe directory happens to exist nearby.
     */
    @Test
    fun doesNotDetectPlainGradleProject()
    {
        val settings = File(autoGenRoot, "settings.gradle.kts")
        settings.writeText(
            """
            rootProject.name = "SomeOtherGame"
            include(":server")
            include(":client")
            """.trimIndent()
        )
        val detection = ColossalDetector.detect(autoGenRoot)
        assertFalse(detection.isColossal1)
        assertEquals(emptyList(), detection.matchedSettingsMarkers)
    }


    /**
     * A missing root directory should be a clean miss, not an exception.
     */
    @Test
    fun returnsFalseOnMissingRoot()
    {
        val bogus = File(tempRoot, "does-not-exist")
        val detection = ColossalDetector.detect(bogus)
        assertFalse(detection.isColossal1)
    }


    /**
     * Detection requires both a settings file with markers AND a TPipe sibling.
     * Markers alone, with no TPipe sibling, should not classify as colossal-1.
     */
    @Test
    fun requiresBothMarkersAndSibling()
    {
        File(tempRoot, "TPipe").deleteRecursively()
        val settings = File(autoGenRoot, "settings.gradle.kts")
        settings.writeText(
            """
            include(":accelbyteSdk")
            include(":kvisionApp")
            """.trimIndent()
        )
        val detection = ColossalDetector.detect(autoGenRoot)
        assertFalse(detection.isColossal1,
            "Markers without TPipe sibling should not classify as colossal-1")
        assertEquals(2, detection.matchedSettingsMarkers.size)
    }


    @Test
    fun oneSettingsMarkerIsInsufficientEvenWhenTpipeIsPresent()
    {
        File(autoGenRoot, "settings.gradle").writeText("include(':accelbyteSdk')\n")

        val detection = ColossalDetector.detect(autoGenRoot)

        assertFalse(detection.isColossal1)
        assertEquals(listOf("accelbyteSdk"), detection.matchedSettingsMarkers)
        assertEquals(listOf("TPipe"), detection.matchedTpipeSiblings)
    }


    @Test
    fun detectsExactlyTwoMarkersWhenTpipeIsAtTheWorkspaceAncestor()
    {
        val nestedRoot = File(tempRoot, "workspace/Autogenesis")
        nestedRoot.mkdirs()
        File(tempRoot, "TPipe").mkdirs()
        File(nestedRoot, "settings.gradle.kts").writeText(
            "include(\":kvisionApp\")\ninclude(\":jukebox\")\n",
        )

        val detection = ColossalDetector.detect(nestedRoot)

        assertTrue(detection.isColossal1)
        assertEquals(listOf("kvisionApp", "jukebox"), detection.matchedSettingsMarkers)
        assertEquals(listOf("TPipe"), detection.matchedTpipeSiblings)
    }
}
