package edu.kit.compiler.error

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import edu.kit.compiler.lexer.Token

data class ColorScheme(
    val comment: TextStyle,
    val error: TextStyle,
    val identifier: TextStyle,
    val keyword: TextStyle,
    val literal: TextStyle,
    val operator: TextStyle,
    val whitespace: TextStyle
) {

    companion object {

        val solarized = ColorScheme(
            comment = TextStyles.dim + TextColors.gray,
            error = TextStyles.underline + TextColors.magenta,
            identifier = TextColors.brightBlue,
            keyword = TextColors.green,
            literal = TextColors.brightCyan,
            operator = TextStyles.bold + TextColors.white,
            whitespace = TextColors.white
        )
    }
}

fun Terminal.printToken(token: Token, colorScheme: ColorScheme = ColorScheme.solarized): Unit = when (token) {
    is Token.Comment -> this.print(colorScheme.comment("/*${token.content}*/"))
    is Token.Eof -> {}
    is Token.ErrorToken -> this.print(colorScheme.error(token.content))
    is Token.Identifier -> this.print(colorScheme.identifier(token.name.text))
    is Token.Keyword -> this.print(colorScheme.keyword(token.type.repr))
    is Token.Literal -> this.print(colorScheme.literal(token.value))
    is Token.Operator -> this.print(colorScheme.operator(token.type.repr))
    is Token.Whitespace -> this.print(colorScheme.whitespace(token.content))
}

fun Terminal.printTokens(tokens: Sequence<Token>, colorScheme: ColorScheme = ColorScheme.solarized) {
    tokens.forEach {
        this.printToken(it, colorScheme)
    }
}
