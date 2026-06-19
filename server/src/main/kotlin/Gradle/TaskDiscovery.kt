package Gradle

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single gradle task as discovered by [TaskDiscovery].
 *
 * @property name The gradle task name (e.g. `build`, `installDist`).
 * @property group The gradle task group (e.g. `build`, `verification`). Empty string
 *                 means the task was not in a group.
 * @property description Optional description from `tasks --all`. May be blank.
 *
 * @since added in v2.
 */
data class GradleTaskDescriptor(
    val name: String,
    val group: String,
    val description: String,
)


/**
 * Discover the list of gradle tasks for a given project root.
 *
 * Implementation shells out to the project's `./gradlew` (or the user-configured
 * [Config.GradleProject.gradleHome]) and parses the `tasks --all --console=plain`
 * output. We use the plain console output so the parser does not have to deal with
 * ANSI escape codes or the progress bar.
 *
 * The parser is intentionally tolerant. Gradle has changed its output format a few
 * times across versions and a hard line-based parse is enough for v1: group headers
 * look like `Build tasks` or `Verification tasks`, task lines look like
 * `taskName - description`, and we tolerate the leading indent and trailing whitespace.
 *
 * @since added in v2.
 */
object TaskDiscovery
{
    /**
     * Run `./gradlew tasks --all --console=plain` against the project root and return
     * the parsed [GradleTaskDescriptor] list.
     *
     * @param projectRoot The root directory of the gradle project (where
     *                    `gradlew` / `gradlew.bat` / `settings.gradle.kts` live).
     * @return The list of discovered tasks. Empty if the project is not a gradle
     *         project, gradlew is missing, or the invocation failed.
     */
    fun discover(projectRoot : File) : List<GradleTaskDescriptor>
    {
        val wrapper = locateWrapper(projectRoot)
            ?: return emptyList()

        // `--no-daemon` keeps each `list-tasks` call self-contained: the
        // wrapper forks a fresh JVM, runs the build, and exits. Without it,
        // the wrapper tries to connect to a daemon left over from a prior
        // build (possibly in another concurrent run on the same machine),
        // which can be in `DaemonStateCoordinator.awaitStop` and silently
        // swallow new build requests, hanging the wrapper forever.
        //
        // `redirectInput(DEVNULL)` prevents gradle from inheriting the
        // parent's stdin and blocking on a read of it (no TTY in CI).
        //
        // `redirectErrorStream(true)` merges stderr into stdout so a single
        // reader thread below can drain both into one buffer.
        val process = ProcessBuilder(
                wrapper.absolutePath,
                "tasks", "--all", "--console=plain",
                "--no-daemon",
            )
            .directory(projectRoot)
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .start()

        // Drain the merged stdout/stderr pipe on a dedicated reader thread
        // while the process runs. With `redirectErrorStream(true)` the
        // two streams share a single ~64KB pipe; gradle's
        // `ThrottlingOutputEventListener` blocks on a monitor while
        // rendering output, so if the parent JVM doesn't keep draining the
        // pipe the buffer fills up, gradle blocks writing more output, and
        // the build deadlocks. Reading on a separate thread avoids the
        // deadlock; `waitFor` then returns when the process exits and we
        // join the reader to collect any final output.
        val outputBuilder = StringBuilder()
        val readerThread = Thread({
            try
            {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(outputBuilder) { outputBuilder.appendLine(line) }
                }
            }
            catch(_: Exception) { /* pipe closed, that's fine */ }
        }, "gradle-task-discovery-reader").apply { isDaemon = true; start() }

        // 10 minutes is a deliberately generous budget: on a fresh
        // `GRADLE_USER_HOME` the wrapper has to download the gradle
        // distribution (~130MB) which alone takes 60-120s, and the
        // multi-project configuration phase + cold-start JIT can add
        // another 30-60s on top.
        val finished = process.waitFor(10, TimeUnit.MINUTES)
        if(!finished)
        {
            process.destroyForcibly()
            readerThread.interrupt()
            readerThread.join(5_000)
            return emptyList()
        }

        // Give the reader a moment to drain anything still in the pipe
        // after the process exited. If it doesn't finish in 5s, take
        // what we have.
        readerThread.join(5_000)
        if(readerThread.isAlive)
        {
            readerThread.interrupt()
        }
        val rawOutput = synchronized(outputBuilder) { outputBuilder.toString() }
        return parseTasksOutput(rawOutput)
    }


    /**
     * Find the gradle wrapper script for the project. Prefers the Unix `gradlew`,
     * falls back to the Windows `gradlew.bat`. Returns null if neither exists.
     *
     * @param projectRoot The root directory of the gradle project.
     * @return The wrapper [File] or null.
     */
    fun locateWrapper(projectRoot : File) : File?
    {
        val unix = File(projectRoot, "gradlew")
        if(unix.exists() && unix.canExecute())
        {
            return unix.absoluteFile
        }
        val bat = File(projectRoot, "gradlew.bat")
        if(bat.exists())
        {
            return bat.absoluteFile
        }
        return null
    }


    /**
     * Parse the `tasks --all --console=plain` output into [GradleTaskDescriptor]s.
     *
     * The format we recognize is the standard gradle 7+/8 layout: a section header
     * line like `Build tasks` or `Verification tasks`, optional `----------`
     * underline, then indented task lines of the form `taskName - description`.
     *
     * @param output The raw stdout of the gradle invocation.
     * @return The parsed task descriptors. May be empty if the output was empty.
     */
    fun parseTasksOutput(output : String) : List<GradleTaskDescriptor>
    {
        val descriptors = mutableListOf<GradleTaskDescriptor>()
        var currentGroup = ""

        for(rawLine in output.lines())
        {
            val line = rawLine.trimEnd()
            if(line.isBlank())
            {
                continue
            }

            //Section headers end with " tasks" or " task" (singular). We use this as a
            //robust heuristic: the line is a header if it doesn't start with whitespace
            //after trimming, doesn't contain a " - " separator, and ends with the word
            //"task" or "tasks".
            if(isGroupHeader(line))
            {
                currentGroup = line.removeSuffix(" tasks").removeSuffix(" task").trim().lowercase()
                continue
            }

            //Task lines are `taskName - description`. The ` - ` separator is gradle's
            //canonical output format; we tolerate variable whitespace.
            val separatorIndex = line.indexOf(" - ")
            if(separatorIndex <= 0)
            {
                continue
            }

            val taskName = line.substring(0, separatorIndex).trim()
            val taskDescription = line.substring(separatorIndex + 3).trim()

            //Sanity: gradle task names cannot contain whitespace.
            if(taskName.isBlank() || taskName.any { it.isWhitespace() })
            {
                continue
            }

            descriptors.add(
                GradleTaskDescriptor(
                    name = taskName,
                    group = currentGroup,
                    description = taskDescription,
                )
            )
        }

        return descriptors
    }


    /**
     * @return True if [line] looks like a gradle task group header.
     */
    private fun isGroupHeader(line : String) : Boolean
    {
        if(line.contains(" - "))
        {
            return false
        }
        val trimmed = line.trim()
        if(trimmed.isEmpty())
        {
            return false
        }
        return trimmed.endsWith(" tasks", ignoreCase = true)
            || trimmed.endsWith(" task", ignoreCase = true)
    }
}
