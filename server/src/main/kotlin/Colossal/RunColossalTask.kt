package Colossal

import Gradle.TaskDiscovery
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Run a single gradle task against a colossal subproject's gradle root. This is the
 * shared runtime path used by both the gradle subcommand tree and the colossal
 * subcommand tree; the gradle runner does not care whether the project is a plain
 * gradle project or a colossal-1 composite.
 *
 * @since added in v2.
 */
fun runColossalSubprojectTask(projectRoot : File, task : String)
{
    val wrapper = TaskDiscovery.locateWrapper(projectRoot)
    if(wrapper == null)
    {
        println("No gradlew script found at ${projectRoot.absolutePath}. " +
                "Run `gradle wrapper` once to generate it.")
        return
    }
    println("Running: ${wrapper.name} $task in ${projectRoot.absolutePath}")
    val process = ProcessBuilder(wrapper.absolutePath, task, "--console=plain")
        .directory(projectRoot)
        .redirectErrorStream(true)
        .start()
    val finished = process.waitFor(30, TimeUnit.MINUTES)
    if(!finished)
    {
        process.destroyForcibly()
        println("Task $task timed out after 30 minutes.")
        return
    }
    val output = process.inputStream.bufferedReader().readText()
    println(output)
    val exit = process.exitValue()
    if(exit != 0)
    {
        println("Task $task exited with code $exit.")
    }
}
