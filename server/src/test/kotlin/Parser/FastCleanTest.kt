package Parser

import Config.UnrealProject
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Filesystem-only tests for fast clean; no Unreal binaries or project files are needed. */
class FastCleanTest {
    private lateinit var tempRoot: File

    @AfterTest
    fun tearDown() {
        if (::tempRoot.isInitialized) tempRoot.deleteRecursively()
    }

    private fun newProjectRoot(): File = Files.createTempDirectory("ubuild-fast-clean-test").toFile()

    private fun File.withMarker(relativePath: String): File = resolve(relativePath).apply {
        parentFile.mkdirs()
        writeText("keep or delete")
    }

    @Test
    fun fullFastCleanRemovesProjectCachesAndPreservesSourceAndPluginCachesWhenDisabled() {
        tempRoot = newProjectRoot()
        val project = UnrealProject().apply { projectRoot = tempRoot.absolutePath }
        val rootIntermediate = tempRoot.withMarker("Intermediate/cache.bin")
        val rootBinary = tempRoot.withMarker("Binaries/Sample.dll")
        val pluginIntermediate = tempRoot.withMarker("Plugins/Sample/Intermediate/cache.bin")
        val pluginBinary = tempRoot.withMarker("Plugins/Sample/Binaries/Sample.dll")
        val source = tempRoot.withMarker("Source/Sample.cpp")
        val pluginContent = tempRoot.withMarker("Plugins/Sample/Content/Sample.uasset")

        fastClean(project, deletePlugins = false, deleteIntermediateOnly = false)

        assertFalse(rootIntermediate.exists())
        assertFalse(rootBinary.exists())
        assertTrue(pluginIntermediate.exists())
        assertTrue(pluginBinary.exists())
        assertTrue(source.exists())
        assertTrue(pluginContent.exists())
    }

    @Test
    fun intermediateOnlyCleanKeepsProjectBinariesButRemovesPluginCachesWhenEnabled() {
        tempRoot = newProjectRoot()
        val project = UnrealProject().apply { projectRoot = tempRoot.absolutePath }
        val rootIntermediate = tempRoot.withMarker("Intermediate/cache.bin")
        val rootBinary = tempRoot.withMarker("Binaries/Sample.dll")
        val pluginIntermediate = tempRoot.withMarker("Plugins/Sample/Intermediate/cache.bin")
        val pluginBinary = tempRoot.withMarker("Plugins/Sample/Binaries/Sample.dll")
        val source = tempRoot.withMarker("Source/Sample.cpp")

        fastClean(project, deletePlugins = true, deleteIntermediateOnly = true)

        assertFalse(rootIntermediate.exists())
        assertTrue(rootBinary.exists())
        assertFalse(pluginIntermediate.exists())
        assertFalse(pluginBinary.exists())
        assertTrue(source.exists())
    }

    @Test
    fun projectWithoutRootDoesNothing() {
        tempRoot = newProjectRoot()
        val sentinel = tempRoot.withMarker("sentinel.txt")
        val project = UnrealProject() // Empty root must not target the process working directory.

        fastClean(project, deletePlugins = true, deleteIntermediateOnly = false)

        assertTrue(sentinel.exists())
        assertTrue(tempRoot.exists())
    }
}
