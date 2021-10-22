package edu.kit.compiler

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("mjavac")

    val config = object : Compiler.Config {
        override val isEcho by parser.option(ArgType.Boolean, fullName = "echo", description = "Write input file to stdout").default(false)
    }

    val file by parser.argument(ArgType.String, fullName = "file", description = "input file")

    parser.parse(args)

    Compiler(config).compile(File(file))
}
