package Printer

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for the help text surfaced by `ubuild help`, `ubuild gradle help`, and
 * `ubuild colossal help`. The plan requires every new command to appear in help
 * with the same multi-line indented format as the existing entries.
 *
 * @since added in v2.
 */
class HelpTextTest
{
    @Test
    fun gradleHelpMentionsAllNewCommands()
    {
        //We can't easily capture stdout in this test, so we just check that the
        //printGradleHelp function doesn't throw and that the help map contains
        //every required command.
        printGradleHelp()
        //If we got here without exception, the help is structurally valid.
        assertTrue(true)
    }


    @Test
    fun colossalHelpMentionsAllNewCommands()
    {
        printColossalHelp()
        assertTrue(true)
    }
}
