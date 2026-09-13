package Printer

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/** Tests that the published help output names each supported subcommand. */
class HelpTextTest
{
    @Test
    fun gradleHelpMentionsAllSupportedCommands()
    {
        val text = captureStandardOutput(::printGradleHelp)
        for(command in listOf("init-task", "init-subproject", "init-project", "list-tasks", "info", "run", "test", "clean"))
        {
            assertContains(text, "$command:")
        }
        assertFalse(text.isBlank())
    }

    @Test
    fun colossalHelpMentionsRegistrationAndInfoCommands()
    {
        val text = captureStandardOutput(::printColossalHelp)
        assertContains(text, "register:")
        assertContains(text, "info:")
        assertFalse(text.isBlank())
    }

    private fun captureStandardOutput(block: () -> Unit): String
    {
        val original = System.out
        val output = ByteArrayOutputStream()
        try
        {
            System.setOut(PrintStream(output, true, Charsets.UTF_8))
            block()
        }
        finally
        {
            System.setOut(original)
        }
        return output.toString(Charsets.UTF_8)
    }
}
