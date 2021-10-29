package edu.kit.compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.file
import kotlin.system.exitProcess

class Cli : CliktCommand(name = "mjavac"), Compiler.Config {
    
    override val mode by option(help = "execution mode").switch(
            "--compile" to Compiler.Mode.Compile,
            "--echo" to Compiler.Mode.Echo,
            "--lextest" to Compiler.Mode.LexTest
    ).default(Compiler.Mode.Compile)
    
    override val parallelization by option("-p", "--parallelization", help = "Target parallelization level. Defaults to 0, which uses all available cores.").uint().default(0u)
    
    override val sourceFile by argument(name = "file", help = "source file").file()
    
    override fun run() {
        val compiler = Compiler(this)
        exitProcess(compiler.compile())
    }
    
}

fun main(args: Array<String>) = Cli().main(args)
