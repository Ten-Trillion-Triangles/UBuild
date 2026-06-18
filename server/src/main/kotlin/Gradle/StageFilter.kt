package Gradle

/**
 * Filter a list of gradle tasks down to the ones that look like "stage"-equivalent
 * packaging tasks. The UBuild `package` command on a gradle project runs the stage
 * picker when the user has not supplied a `--stage-task`, and the picker uses this
 * filter to build the menu.
 *
 * Matching rules (case-insensitive substring matches against the task name):
 *  - starts with `install` (installDist, installShadowDist, etc.)
 *  - starts with `dist` (distZip, distTar, etc.)
 *  - starts with `stage`
 *  - contains `Stage` or `Dist` or `Install` anywhere
 *
 * @since added in v2.
 */
object StageFilter
{
    /**
     * @param tasks The full task list, typically from [TaskDiscovery.discover].
     * @return The subset of [tasks] that look like staging/packaging tasks, in the
     *         same order they appeared in the input.
     */
    fun filter(tasks : List<GradleTaskDescriptor>) : List<GradleTaskDescriptor>
    {
        return tasks.filter { it.matchesStagePattern() }
    }


    /**
     * @return True if this task name looks like a staging/packaging task.
     */
    fun GradleTaskDescriptor.matchesStagePattern() : Boolean
    {
        val n = name
        if(n.startsWith("install", ignoreCase = true)) return true
        if(n.startsWith("dist", ignoreCase = true)) return true
        if(n.startsWith("stage", ignoreCase = true)) return true
        if(n.contains("Stage", ignoreCase = true)) return true
        if(n.contains("Dist", ignoreCase = true)) return true
        if(n.contains("Install", ignoreCase = true)) return true
        return false
    }
}
