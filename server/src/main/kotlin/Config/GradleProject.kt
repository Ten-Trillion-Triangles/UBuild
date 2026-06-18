package Config

import kotlinx.serialization.SerialName

/**
 * Project configuration for a generic Gradle project.
 *
 * The Gradle project type covers the common gradle case: a project with a settings.gradle.kts
 * and one or more build.gradle.kts files. The runner shells out to `./gradlew` (or a
 * user-configured gradle binary) and treats gradle tasks as first-class verbs.
 *
 * Colossal 1 projects embed a [ColossalProject.gradleSubproject] of this shape, so the gradle
 * runner code path is shared between [GradleProject] and [ColossalProject].
 *
 * @since added in v2 to support non-Unreal build targets.
 */
@kotlinx.serialization.Serializable
@SerialName("gradle")
data class GradleProject(
    @kotlinx.serialization.Transient val init: Boolean = true,
)
    : Project
{
    //User-defined alias for the gradle project.
    override var projectName: String = ""

    //Root folder of the gradle project (the directory that contains settings.gradle.kts).
    override var projectRoot: String = ""

    //Where packaged outputs should land. Mirrors [UnrealProject.archivePath] for symmetry.
    override var archivePath: String = ""

    //Override-able path to a gradle install. Empty means use the project's own gradlew.
    var gradleHome: String = ""

    //Override-able path to a JDK. Empty means use JAVA_HOME or java on PATH.
    var javaHome: String = ""

    //Extra JVM args passed to gradle, e.g. "-Xmx4g -XX:MaxMetaspaceSize=1g".
    var jvmArgs: String = ""

    //The default gradle task the top-level `ubuild build <alias>` should run.
    var defaultTask: String = "build"

    /**
     * Default gradle task the top-level `ubuild package <alias>` should run when the user
     * has not supplied a `--stage-task` and the stage picker has been disabled. Most gradle
     * projects do not have a canonical "stage" verb so this is just the user's preferred
     * default; the stage picker is the primary path.
     */
    var defaultStageTask: String = "installDist"

    /**
     * Paths to .env / config.props files that the introspection wizard should read.
     * Stored as repo-relative paths so the project is portable.
     */
    var envFiles: MutableList<String> = mutableListOf()

    /**
     * Map of ubuild subproject alias to gradle subproject path (":foo:bar" or ":foo").
     * Lets the user call `ubuild build MyProj server` to run `:server:build` instead of
     * the root project's build.
     */
    var moduleSubprojectMap: MutableMap<String, String> = mutableMapOf()
}
