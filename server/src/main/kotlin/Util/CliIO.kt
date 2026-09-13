package Util

/**
 * Small adapter around interactive console access. Production defaults to standard
 * input/output; tests can replace both sides for one command invocation.
 */
internal object CliIO
{
    @Volatile
    private var input: () -> String = { kotlin.io.readln() }

    @Volatile
    private var output: (String) -> Unit = { kotlin.io.println(it) }

    fun readLine(): String = input()

    fun printLine(message: Any? = "") = output(message.toString())

    fun <T> withAdapters(inputReader: () -> String, outputWriter: (String) -> Unit, block: () -> T): T
    {
        val previousInput = input
        val previousOutput = output
        input = inputReader
        output = outputWriter
        return try
        {
            block()
        }
        finally
        {
            input = previousInput
            output = previousOutput
        }
    }
}
