package edu.kit.compiler

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("mjavac")

    val config = object : Compiler.Config {
        override val isEcho by parser.option(
            ArgType.Boolean,
            fullName = "echo",
            description = "Write input file to stdout"
        ).default(false)
        override val targetParallelization by parser.option(
            ArgTypes.UInt, fullName = "parallelization",
            shortName = "p",
            description = "Target parallelization level. Defaults to 0, which uses all available cores."
        ).default(0u)
    }

    val filePath by parser.argument(ArgType.String, fullName = "file", description = "input file")

    parser.parse(args)

    exitProcess(Compiler(config, filePath).compile())
}
