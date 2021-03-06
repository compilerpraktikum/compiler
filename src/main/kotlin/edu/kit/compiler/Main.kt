package edu.kit.compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import edu.kit.compiler.utils.Logger
import edu.kit.compiler.utils.choices
import edu.kit.compiler.utils.distinct
import kotlin.system.exitProcess

class Cli : CliktCommand(name = "mjavac"), Compiler.Config {

    override val mode by option(help = "execution mode")
        .switch(Compiler.Mode.values().associateBy { "--${it.cliFlag}" }).default(Compiler.Mode.Compile)

    override val sourceFile by argument(name = "file", help = "source file").path()

    override val outputFile by option("-o", "--out", help = "name of the generated executable", envvar = "COMPILER_OUT_FILE").path()

    override val dump by option("--dump", help = "output intermediate compilation results (separate multiple values by comma)")
        .choices(Compiler.Dump.values().associateBy { it.cliFlag }).distinct()

    override val optimizationLevel by option(help = "optimization level (higher number = more optimizations)")
        .switch(Compiler.OptimizationLevel.values().associateBy { "-O${it.intValue}" })
        .defaultLazy {
            System.getenv("COMPILER_OPTIMIZATION_LEVEL")
                ?.let {
                    val level = it.toIntOrNull() ?: throw CliktError("invalid optimization level: $it")
                    Compiler.OptimizationLevel.of(level) ?: throw CliktError("unknown optimization level: $level")
                } ?: Compiler.OptimizationLevel.Base
        }

    private val verbosity by option("-v", "--verbosity", help = "set logging level (default: info)")
        .choice(Logger.Level.values().associateBy { it.name.lowercase() }).default(Logger.Level.INFO)

    override fun run() {
        Logger.level = verbosity
        val compiler = Compiler(this)
        exitProcess(compiler.compile())
    }
}

fun main(args: Array<String>) = Cli().main(args)
