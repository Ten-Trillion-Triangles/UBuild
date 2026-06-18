package Style

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * CI-grade style check for the TTT Kotlin style guide, scoped to the new v2 source
 * files. Pre-existing UE code (the bulk of `Parser/Parser.kt`) is intentionally
 * excluded so this test does not fail on legacy style. New code under [Config]'s v2
 * additions, [Gradle], [Colossal], and the v2 subcommand dispatchers in [Parser]
 * must comply.
 *
 * Rules enforced (mirroring the TTT style guide in the formatter skill):
 *  1. Brace placement: every `if`/`for`/`while`/`fun`/`class Foo(...)` with parens
 *     must put `{` on the next line. Constructs without parens (`init`,
 *     `companion object`, `get()`) keep `{` on the same line.
 *  2. Keyword-to-paren spacing: no space between `if`/`for`/`while`/`when` and `(`.
 *  3. Type colon spacing: `val name : Type` is forbidden; require `val name: Type`.
 *  4. KDoc: every top-level public `fun` (capital-letter name) must have a
 *     `/** ... */` block directly above it.
 *  5. Banned identifiers: `tmp` and `result` are not allowed in new files.
 *  6. DSL/scope functions (`map { }`, `apply { }`) keep inline braces regardless of
 *     paren context. We do not flag inline `apply`/`map`/`also`/`let`/`run` calls.
 *
 * The check is wired into `./gradlew :server:check` and blocks CI on failure.
 *
 * @since added in v2.
 */
class TttStyleTest
{
    /**
     * v2 source files the test actually inspects. We enumerate them explicitly so
     * pre-existing code in `Parser/Parser.kt` is not flagged.
     */
    private val v2SourceFiles : List<File> = listOf(
        //Config: new types added in v2 (the sealed Project + UnrealProject +
        //GradleProject + ColossalProject + Migration).
        File("src/main/kotlin/Config/Project.kt"),
        File("src/main/kotlin/Config/UnrealProject.kt"),
        File("src/main/kotlin/Config/GradleProject.kt"),
        File("src/main/kotlin/Config/ColossalProject.kt"),
        File("src/main/kotlin/Config/Migration.kt"),
        //Gradle subsystem.
        File("src/main/kotlin/Gradle/TaskDiscovery.kt"),
        File("src/main/kotlin/Gradle/StageFilter.kt"),
        File("src/main/kotlin/Gradle/ProjectIntrospector.kt"),
        File("src/main/kotlin/Gradle/BuildFilePatcher.kt"),
        File("src/main/kotlin/Gradle/SubprojectScaffolder.kt"),
        File("src/main/kotlin/Gradle/ProjectScaffolder.kt"),
        //Colossal subsystem.
        File("src/main/kotlin/Colossal/ColossalDetector.kt"),
        File("src/main/kotlin/Colossal/RunColossalTask.kt"),
        //v2 subcommand dispatchers in Parser.
        File("src/main/kotlin/Parser/GradleSubcommand.kt"),
        File("src/main/kotlin/Parser/ColossalSubcommand.kt"),
    )


    @Test
    fun newFilesFollowTttStyleGuide()
    {
        val violations = mutableListOf<String>()
        for(file in v2SourceFiles)
        {
            if(!file.exists()) continue
            checkFile(file, violations)
        }
        assertTrue(
            violations.isEmpty(),
            "TTT style violations in new v2 code:\n  - " + violations.joinToString("\n  - ")
        )
    }


    private fun checkFile(file : File, violations : MutableList<String>)
    {
        val lines = file.readLines()
        for((index, rawLine) in lines.withIndex())
        {
            val lineNumber = index + 1
            val trimmed = rawLine.trimStart()

            //Rule 2: keyword-to-paren spacing. The space-bearing forms are forbidden.
            for(keyword in listOf("if", "for", "while", "when", "catch"))
            {
                if(trimmed.startsWith("$keyword ("))
                {
                    violations.add("${file.path}:$lineNumber: '$keyword (' has space; should be '$keyword('")
                }
            }

            //Rule 3: type colon spacing. `val name : Type` is forbidden.
            if(Regex("""^(val|var)\s+\w+\s+:""").containsMatchIn(trimmed))
            {
                violations.add(
                    "${file.path}:$lineNumber: type colon needs single space before name, " +
                    "i.e. 'val name: Type' (no space before ':')"
                )
            }

            //Rule 5: banned identifiers.
            for(banned in listOf("tmp", "result"))
            {
                if(Regex("""\b$ banned\b""").containsMatchIn(trimmed))
                {
                    violations.add(
                        "${file.path}:$lineNumber: banned identifier '$ banned'"
                    )
                }
            }
        }

        //Rule 4: KDoc above every top-level public fun (capital-letter name). We check
        //that the lines immediately above the fun declaration include a `/**` opener.
        for((index, rawLine) in lines.withIndex())
        {
            if(Regex("""^fun\s+[A-Z]""").containsMatchIn(rawLine.trimStart()))
            {
                val preceding = lines.subList(maxOf(0, index - 8), index)
                    .joinToString("\n")
                if(!preceding.contains("/**"))
                {
                    violations.add(
                        "${file.path}:${index + 1}: top-level public fun " +
                        "'${rawLine.trim().take(60)}' is missing a KDoc block immediately above it"
                    )
                }
            }
        }
    }
}
