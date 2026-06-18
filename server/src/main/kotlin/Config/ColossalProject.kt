package Config

import kotlinx.serialization.SerialName

/**
 * Project configuration for a Colossal-engine project.
 *
 * Colossal 1 is the current TPipe + Autogenesis stack: a multi-module gradle project where
 * TPipe is the engine library and Autogenesis is the consuming product. We model it as a
 * [GradleProject] composite with a Colossal-specific fingerprint detector; the runtime
 * execution path delegates to the gradle runner.
 *
 * Colossal 2 is a future successor. The [isImplemented] flag is the single point of truth
 * for whether a colossal project can actually run; colossal-2 stubs set it to `false` and
 * every command short-circuits to a clear "not yet implemented" error.
 *
 * @since added in v2 to support the colossal engine family.
 */
@kotlinx.serialization.Serializable
@SerialName("colossal")
data class ColossalProject(
    @kotlinx.serialization.Transient val init: Boolean = true,
)
    : Project
{
    //User-defined alias for the colossal project.
    override var projectName: String = ""

    //Root folder of the colossal project on disk.
    override var projectRoot: String = ""

    //Where packaged outputs should land.
    override var archivePath: String = ""

    //"colossal-1" or "colossal-2". Used by the detector to pick the right fingerprint set.
    var engineVersion: String = "colossal-1"

    /**
     * The gradle subproject that actually holds the build files. For colossal-1 this is the
     * Autogenesis settings.gradle.kts root. The runner treats it as a [GradleProject].
     */
    var gradleSubproject: GradleProject = GradleProject()

    /**
     * False for colossal-2 stubs. Every colossal runner checks this first and refuses to
     * execute if it is false, so users get a clean error instead of a confusing gradle failure.
     */
    var isImplemented: Boolean = true
}
