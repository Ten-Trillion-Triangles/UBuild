package Util

import java.io.File
import java.util.concurrent.TimeUnit

/** Shared process launch seam for external build tools. */
internal object ProcessRuntime
{
    @Volatile
    private var launcher: (ProcessBuilder) -> Process = { it.start() }

    @Volatile
    var taskDiscoveryTimeoutMillis: Long = TimeUnit.MINUTES.toMillis(10)

    @Volatile
    var colossalTaskTimeoutMillis: Long = TimeUnit.MINUTES.toMillis(30)

    fun start(builder: ProcessBuilder): Process = launcher(builder)

    fun <T> withOverrides(
        processLauncher: (ProcessBuilder) -> Process,
        taskDiscoveryTimeoutMillis: Long = this.taskDiscoveryTimeoutMillis,
        colossalTaskTimeoutMillis: Long = this.colossalTaskTimeoutMillis,
        block: () -> T,
    ): T
    {
        val previousLauncher = launcher
        val previousDiscoveryTimeout = this.taskDiscoveryTimeoutMillis
        val previousColossalTimeout = this.colossalTaskTimeoutMillis
        launcher = processLauncher
        this.taskDiscoveryTimeoutMillis = taskDiscoveryTimeoutMillis
        this.colossalTaskTimeoutMillis = colossalTaskTimeoutMillis
        return try
        {
            block()
        }
        finally
        {
            launcher = previousLauncher
            this.taskDiscoveryTimeoutMillis = previousDiscoveryTimeout
            this.colossalTaskTimeoutMillis = previousColossalTimeout
        }
    }
}

/** Test-only home override used by config lifecycle tests. */
internal object HomeFolderOverride
{
    @Volatile
    var folder: File? = null
}
