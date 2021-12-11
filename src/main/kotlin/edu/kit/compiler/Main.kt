package edu.kit.compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import kotlin.system.exitProcess

class Cli : CliktCommand(name = "mjavac"), Compiler.Config {

    override val mode by option(help = "execution mode")
        .switch(Compiler.Mode.values().associateBy { "--${it.cliFlag}" }).default(Compiler.Mode.Compile)

    override val sourceFile by argument(name = "file", help = "source file").path()

    override val outputFile by option("-o", "--out", help = "name of the generated executable", envvar = "COMPILER_OUT_FILE").path()

    override val dump by option("--dump", help = "output intermediate compilation results (separate multiple values by comma)")
        .choices(Compiler.Dump.values().associateBy { it.cliFlag }).distinct()

    private val verbose by option("-v", "--verbose", help = "enable verbose logging").flag(default = false)

    override fun run() {
        Logger.level = if (verbose) Logger.Level.ALL else Logger.Level.INFO
        val compiler = Compiler(this)
        exitProcess(compiler.compile())
    }
}

fun main(args: Array<String>) = Cli().main(args)
