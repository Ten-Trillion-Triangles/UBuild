package Parser

import Util.CliIO

// Same-package functions let the existing command handlers use a replaceable
// console without rewriting their prompt and output logic.
internal fun readln(): String = CliIO.readLine()

internal fun println() = CliIO.printLine()

internal fun println(message: Any?) = CliIO.printLine(message)
