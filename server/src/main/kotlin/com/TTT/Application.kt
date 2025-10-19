package com.TTT

import Globals.env
import Parser.parseInput
import Printer.printEntry
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {


    env.loadConfig() //Load the ubuild config file located at ~/.ubuild/config.json
    env.setArgs(args)

    if(env.getArgs().isEmpty())
    {
        printEntry()
        parseInput()
    }

    else
    {
        parseInput()
    }


}

fun Application.module() {
    configureAdministration()
    configureSockets()
    configureSerialization()
    configureFrameworks()
    configureMonitoring()
    configureHTTP()
    configureRouting()
}
