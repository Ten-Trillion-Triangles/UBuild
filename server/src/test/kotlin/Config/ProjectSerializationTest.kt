package Config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectSerializationTest
{
    @Test
    fun engineRoundTripsAllProjectTypesAndNestedColossalGradleSettings()
    {
        val engine = Engine().apply {
            engineRoot = "/fake/engine"
            projects["unreal"] = UnrealProject().apply {
                projectName = "unreal"
                projectRoot = "/projects/unreal"
                uprojectPath = "/projects/unreal/game.uproject"
                projectTarget = "Game"
                buildStringList["Editor"] = BuildString().apply {
                    innerMap["Development"] = "fake-build Editor Linux Development"
                }
            }
            projects["gradle"] = GradleProject().apply {
                projectName = "gradle"
                projectRoot = "/projects/gradle"
                gradleHome = "/opt/gradle"
                javaHome = "/opt/jdk"
                jvmArgs = "-Xmx2g -Dfile.encoding=UTF-8"
                defaultTask = "check"
                defaultStageTask = "installDist"
                envFiles += listOf(".env", "gradle.properties")
                moduleSubprojectMap["server"] = ":backend"
            }
            projects["colossal"] = ColossalProject().apply {
                projectName = "colossal"
                projectRoot = "/projects/colossal"
                engineVersion = "colossal-1"
                gradleSubproject = GradleProject().apply {
                    projectName = "colossal"
                    projectRoot = "/projects/colossal/app"
                    gradleHome = "/opt/colossal-gradle"
                    javaHome = "/opt/colossal-jdk"
                    jvmArgs = "-Xmx4g"
                    moduleSubprojectMap["server"] = ":server"
                }
            }
        }
        val json = Json { encodeDefaults = true }

        val decoded = json.decodeFromString<Engine>(json.encodeToString(engine))

        assertEquals("/fake/engine", decoded.engineRoot)
        val unreal = assertIs<UnrealProject>(decoded.projects["unreal"])
        assertEquals("game.uproject", unreal.uprojectPath.substringAfterLast('/'))
        assertEquals("fake-build Editor Linux Development", unreal.buildStringList["Editor"]?.innerMap?.get("Development"))
        val gradle = assertIs<GradleProject>(decoded.projects["gradle"])
        assertEquals("/opt/gradle", gradle.gradleHome)
        assertEquals(listOf(".env", "gradle.properties"), gradle.envFiles)
        assertEquals(":backend", gradle.moduleSubprojectMap["server"])
        val colossal = assertIs<ColossalProject>(decoded.projects["colossal"])
        assertEquals("/projects/colossal/app", colossal.gradleSubproject.projectRoot)
        assertEquals("/opt/colossal-gradle", colossal.gradleSubproject.gradleHome)
        assertEquals("-Xmx4g", colossal.gradleSubproject.jvmArgs)
        assertEquals(":server", colossal.gradleSubproject.moduleSubprojectMap["server"])
    }
}
