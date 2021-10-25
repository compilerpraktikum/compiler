package edu.kit.compiler

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("mjavac")
    val consoleOutputManager = ConsoleOutputManager("Main")

    val config = object : Compiler.Config {
        override val isEcho by parser.option(ArgType.Boolean, fullName = "echo", description = "Write input file to stdout").default(false)
    }

    val filePath by parser.argument(ArgType.String, fullName = "file", description = "input file")

    parser.parse(args)

    val file = File(filePath).apply {
        if (!isFile) consoleOutputManager.error("File does not exist or is a directory")
    }

    Compiler(config).compile(file)
}
