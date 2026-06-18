package Gradle

import java.io.File

/**
 * Patch a `build.gradle.kts` to add a new `tasks.register { ... }` block.
 *
 * The patcher is a small, conservative string editor. It does not parse Kotlin; it
 * locates a sentinel insertion point (the end of the file by default, or before a
 * user-specified anchor), and appends a new block there. This is enough for the
 * "scaffold a new gradle task" wizard, which produces well-formed blocks we control.
 *
 * @since added in v2.
 */
object BuildFilePatcher
{
    /**
     * Render the new `tasks.register { ... }` block as a string.
     *
     * @param taskName The new task name (e.g. "myTask"). Must be a valid Kotlin
     *                 identifier; the patcher does not validate this.
     * @param group Optional task group. Blank means no `group =` line.
     * @param description Optional task description. Blank means no `description =` line.
     * @param dependsOn Optional list of task names to add as `dependsOn`. Empty means
     *                  no `dependsOn` line.
     * @param body Optional body to put inside the `doLast { ... }`. Blank means the
     *             block gets a no-op body with a comment placeholder.
     * @return The rendered block, terminated with a newline.
     */
    fun renderTaskBlock(
        taskName : String,
        group : String = "",
        description : String = "",
        dependsOn : List<String> = emptyList(),
        body : String = "",
    ) : String
    {
        val lines = mutableListOf<String>()
        lines.add("")
        lines.add("//Scaffolded by ubuild gradle init-task on ${java.time.LocalDate.now()}.")
        lines.add("tasks.register(\"$taskName\") {")
        if(group.isNotBlank())
        {
            lines.add("    group = \"$group\"")
        }
        if(description.isNotBlank())
        {
            lines.add("    description = \"$description\"")
        }
        if(dependsOn.isNotEmpty())
        {
            lines.add("    dependsOn(${dependsOn.joinToString(", ") { "\"$it\"" }})")
        }
        lines.add("    doLast {")
        if(body.isBlank())
        {
            lines.add("        //TODO: implement the task body.")
        }
        else
        {
            for(bodyLine in body.lines())
            {
                lines.add("        $bodyLine")
            }
        }
        lines.add("    }")
        lines.add("}")
        return lines.joinToString("\n") + "\n"
    }


    /**
     * Apply [renderedBlock] to [buildFile] at the end of the file (the default
     * insertion point) or before the line containing [anchor] if specified.
     *
     * @return The new file contents as a string. The on-disk file is not modified;
     *         callers should write the returned string themselves.
     */
    fun applyBlock(buildFile : File, renderedBlock : String, anchor : String? = null) : String
    {
        val original = if(buildFile.exists()) buildFile.readText() else ""
        if(original.isEmpty())
        {
            return renderedBlock
        }

        if(anchor == null)
        {
            //Default: append at the end. We avoid double-newlines by trimming trailing
            //whitespace from the original before appending.
            val trimmed = original.trimEnd() + "\n"
            return trimmed + renderedBlock
        }

        //Anchor path: find the first line that contains the anchor and insert before it.
        val lines = original.lines()
        val anchorIndex = lines.indexOfFirst { it.contains(anchor) }
        if(anchorIndex < 0)
        {
            //Anchor not found: fall back to append.
            val trimmed = original.trimEnd() + "\n"
            return trimmed + renderedBlock
        }
        val before = lines.subList(0, anchorIndex).joinToString("\n")
        val after = lines.subList(anchorIndex, lines.size).joinToString("\n")
        return before.trimEnd() + "\n" + renderedBlock + after
    }
}
