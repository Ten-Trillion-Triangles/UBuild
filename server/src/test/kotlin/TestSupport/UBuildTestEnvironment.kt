package TestSupport

import Globals.env
import Util.HomeFolderOverride
import java.io.File
import java.nio.file.Files

/** Isolates the global configuration singleton from the real user's home directory. */
internal class UBuildTestEnvironment : AutoCloseable
{
    private val previousHome = HomeFolderOverride.folder
    val home: File = Files.createTempDirectory("ubuild-test-home").toFile()

    init
    {
        HomeFolderOverride.folder = home
        env.resetForTesting()
        env.loadConfig()
        env.setArgs(emptyArray())
    }

    override fun close()
    {
        env.resetForTesting()
        HomeFolderOverride.folder = previousHome
        home.deleteRecursively()
    }
}
