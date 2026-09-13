package Util

import Config.Engine
import Config.UnrealProject
import Enums.LineEnding
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Regression tests for Unreal command construction and local text/argument helpers. */
class UnrealHelperTest {
    private lateinit var tempRoot: File

    @AfterTest
    fun tearDown() {
        if (::tempRoot.isInitialized) tempRoot.deleteRecursively()
    }

    private fun newTempRoot(): File = Files.createTempDirectory("ubuild-unreal-helper-test").toFile()

    @Test
    fun packageCommandsUseProjectEngineAndNestedTargetConfigurationPlatformKeys() {
        tempRoot = newTempRoot()
        val project = UnrealProject().apply {
            projectName = "Sample"
            projectTarget = "SampleGame"
            uprojectPath = File(tempRoot, "Sample.uproject").absolutePath
            archivePath = File(tempRoot, "archive").absolutePath
        }
        val engine = Engine().apply { uat = "/opt/Unreal/Engine/Build/BatchFiles/RunUAT.sh" }

        generatePackageString(project, engine)

        val command = project.packageList.getValue("SampleGame")
            .buildMap.getValue("Shipping")
            .innerMap.getValue("Linux")
        assertEquals(
            "${engine.uat} BuildCookRun -project=${project.uprojectPath} " +
                "-ScriptsForProject=${project.uprojectPath} -noP4 " +
                "-Platform=Linux -clientconfig=Shipping -serverconfig=Shipping -cook -allmaps -build -stage " +
                "-archive -target=SampleGame -archivedirectory=${project.archivePath}",
            command,
        )
        assertEquals(project.targetList.size, project.packageList.size)
        assertEquals(project.configList.toSet(), project.packageList.getValue("SampleGame").buildMap.keys)
        assertEquals(project.platformList.toSet(), project.packageList.getValue("SampleGame")
            .buildMap.getValue("Shipping").innerMap.keys)
        assertSame(project, engine.projects.getValue("Sample"))
    }

    @Test
    fun buildCommandsIncludeTheConfiguredExecutableTargetAndProject() {
        val project = UnrealProject().apply {
            projectTarget = "SampleGame"
            uprojectPath = "/tmp/Sample/Sample.uproject"
        }
        val engine = Engine().apply { buildSh = "/opt/Unreal/Engine/Build/BatchFiles/Linux/Build.sh" }

        generateBuildStrings(project, engine)

        val command = project.buildStringList.getValue("").innerMap.getValue("Development")
        assertEquals(
            "${engine.buildSh} SampleGame ${getOs()} Development -Project=${project.uprojectPath} -buildscw",
            command,
        )
        assertEquals(project.targetList.size, project.buildStringList.size)
        assertEquals(project.configList.toSet(), project.buildStringList.getValue("Editor").innerMap.keys)
    }

    @Test
    fun commandArgumentHelpersKeepArgumentOrderAndRepeatedArguments() {
        val command = "RunUAT.sh BuildCookRun -cook -cook -target=Sample"

        assertEquals(
            listOf("RunUAT.sh", "BuildCookRun", "-cook", "-cook", "-target=Sample"),
            splitProgramString(command),
        )
        assertEquals(" -cook -cook -target=Sample", getStringFromArgsbyIndex(command.split(" "), 2))
        assertEquals("", getStringFromArgsbyIndex(emptyList(), 0))
    }

    @Test
    fun detectsEachSupportedLineEndingAndUnknownText() {
        assertEquals(LineEnding.Windows, getLineEnding("first\r\nsecond\r\n"))
        assertEquals(LineEnding.Unix, getLineEnding("first\nsecond\n"))
        assertEquals(LineEnding.Mac, getLineEnding("first\rsecond\r"))
        assertEquals(LineEnding.Unknown, getLineEnding("single line without terminator"))
    }

    @Test
    fun convertsClassicMacLineEndingsAndLeavesOtherFilesUnchanged() {
        tempRoot = newTempRoot()
        val macFile = File(tempRoot, "mac.txt").apply { writeBytes("one\rtwo\r".toByteArray()) }
        val windowsFile = File(tempRoot, "windows.txt").apply { writeBytes("one\r\ntwo\r\n".toByteArray()) }
        val unixFile = File(tempRoot, "unix.txt").apply { writeBytes("one\ntwo\n".toByteArray()) }
        val unknownFile = File(tempRoot, "unknown.txt").apply { writeBytes("one".toByteArray()) }

        assertTrue(convertLineEnding(macFile))
        assertEquals("one\ntwo\n", macFile.readText())
        assertTrue(convertLineEnding(windowsFile))
        assertEquals("one\r\ntwo\r\n", windowsFile.readText())
        assertTrue(convertLineEnding(unixFile))
        assertEquals("one\ntwo\n", unixFile.readText())
        assertFalse(convertLineEnding(unknownFile))
        assertEquals("one", unknownFile.readText())
    }
}
