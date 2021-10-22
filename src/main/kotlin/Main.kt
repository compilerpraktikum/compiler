import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

data class Config(
    val isEcho: Boolean
)

fun main(args: Array<String>) {
    val parser = ArgParser("mjavac")

    val isEchoMode by parser.option(ArgType.Boolean, fullName = "echo", description = "Write input file to stdout").default(false)

    val file by parser.argument(ArgType.String, fullName = "file", description = "input file")

    parser.parse(args)

    val config = Config(
        isEcho = isEchoMode
    )

    Compiler(config).compile(File(file))
}
