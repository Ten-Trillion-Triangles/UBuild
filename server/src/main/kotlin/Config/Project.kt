package Config

/**
 * Sealed hierarchy for every kind of project UBuild can manage.
 *
 * We have to be careful here: the v1 on-disk format was a single flat [Config.Project] data
 * class that mixed Unreal Engine fields into a generic shell. We are intentionally replacing
 * that with a sealed type so the parser can dispatch on the concrete subtype and so future
 * engine types (gradle, colossal, anything else) can be added without touching the existing
 * UE code paths.
 *
 * @note for future me: every concrete subtype lives in its own file under [Config] and is
 * registered in [Config.Migration] so the v1 -> v2 config rewrite knows about it.
 */
@kotlinx.serialization.Serializable
sealed interface Project
{
    /** User-defined alias used to invoke the project from the CLI. */
    var projectName: String

    /** Path to the project root folder on disk. */
    var projectRoot: String

    /** Path that build/packaging outputs will be staged or copied to. */
    var archivePath: String
}
