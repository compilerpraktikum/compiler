package edu.kit.compiler.lex

import edu.kit.compiler.Token

/**
 * lexicographic analysis and tokenization of an input stream.
 *
 * @param input input abstracted in a ring buffer
 * @param stringTable string table of current compilation run
 */
@Suppress("BlockingMethodInNonBlockingContext")
class Lexer(
    fileName: String,
    input: InputProvider,
    stringTable: StringTable,
    private val printWarnings: Boolean = true
) : AbstractLexer(fileName, input, stringTable) {

    companion object {
        val identFirstCharRegex: Regex = Regex("[_a-zA-Z]")
        val identRestCharsRegex: Regex = Regex("[_a-zA-Z0-9]")
        val literalCharRegex: Regex = Regex("[0-9]")
        val whitespaceCharRegex: Regex = Regex("[ \\n\\r\\t]")
    }

    override suspend fun scanToken(): Token = when (val c = next()) {
        ' ', '\n', '\r', '\t' -> scanWhitespace(c)
        '/' -> scanDiv()
        '!' -> scanNot()
        '(' -> Token.Operator(Token.Operator.Type.LParen)
        ')' -> Token.Operator(Token.Operator.Type.RParen)
        '*' -> scanMul()
        '+' -> scanPlus()
        ',' -> Token.Operator(Token.Operator.Type.Comma)
        '-' -> scanMinus()
        '.' -> Token.Operator(Token.Operator.Type.Dot)
        ':' -> Token.Operator(Token.Operator.Type.Colon)
        ';' -> Token.Operator(Token.Operator.Type.Semicolon)
        '<' -> scanLt()
        '=' -> scanAssign()
        '>' -> scanGt()
        '?' -> Token.Operator(Token.Operator.Type.QuestionMark)
        '%' -> scanModulo()
        '&' -> scanBitAnd()
        '[' -> Token.Operator(Token.Operator.Type.LeftBracket)
        ']' -> Token.Operator(Token.Operator.Type.RightBracket)
        '^' -> scanXor()
        '{' -> Token.Operator(Token.Operator.Type.LeftBrace)
        '}' -> Token.Operator(Token.Operator.Type.RightBrace)
        '~' -> Token.Operator(Token.Operator.Type.BitNot)
        '|' -> scanBitOr()
        '0' -> Token.Literal("0")
        '1', '2', '3', '4', '5', '6', '7', '8', '9' -> scanNonZeroLiteral(c)
        else -> scanIdent(c)
    }

    private suspend inline fun scanWhitespace(startChar: Char): Token {
        return Token.Whitespace(
            buildString {
                append(startChar)

                while (whitespaceCharRegex.matches(peek().toString())) {
                    append(next())
                }
            }
        )
    }

    private suspend inline fun scanNonZeroLiteral(startDigit: Char): Token {
        return Token.Literal(
            buildString {
                append(startDigit)
                while (literalCharRegex.matches(peek().toString())) {
                    append(next())
                }
            }
        )
    }

    private suspend inline fun scanIdent(startChar: Char): Token {
        val identBuilder: StringBuilder = StringBuilder().append(startChar)
        if (!identFirstCharRegex.matches(startChar.toString())) {
            return Token.ErrorToken(startChar.toString(), "unexpected character: $startChar")
        }
        while (identRestCharsRegex.matches(peek().toString())) {
            identBuilder.append(next())
        }

        val (identifier, stringTableEntry) = stringTable.tryRegisterIdentifier(identBuilder.toString())

        return if (stringTableEntry.isKeyword) {
            Token.Keyword(Token.Keyword.Type.from(identifier)!!)
        } else {
            Token.Identifier(identifier)
        }
    }

    private suspend inline fun scanBitOr(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.OrAssign)
            }
            '|' -> {
                next()
                Token.Operator(Token.Operator.Type.Or)
            }
            else -> Token.Operator(Token.Operator.Type.BitOr)
        }
    }

    private suspend inline fun scanBitAnd(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.AndAssign)
            }
            '&' -> {
                next()
                Token.Operator(Token.Operator.Type.And)
            }
            else -> Token.Operator(Token.Operator.Type.BitAnd)
        }
    }

    private suspend inline fun scanXor(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.XorAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Xor)
        }
    }

    private suspend inline fun scanModulo(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.ModAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mod)
        }
    }

    private suspend inline fun scanGt(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.GtEq)
            }
            '>' -> {
                next()
                when (peek()) {
                    '=' -> {
                        next()
                        Token.Operator(Token.Operator.Type.RightShiftSEAssign)
                    }
                    '>' -> {
                        next()
                        if (peek() == '=') {
                            next()
                            Token.Operator(Token.Operator.Type.RightShiftAssign)
                        } else Token.Operator(Token.Operator.Type.RightShift)
                    }
                    else -> {
                        Token.Operator(Token.Operator.Type.RightShiftSE)
                    }
                }
            }
            else -> Token.Operator(Token.Operator.Type.Gt)
        }
    }

    private suspend inline fun scanAssign(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.Eq)
            }
            else -> Token.Operator(Token.Operator.Type.Assign)
        }
    }

    private suspend inline fun scanLt(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.LtEq)
            }
            '<' -> {
                next()
                if (peek() == '=') {
                    next()
                    Token.Operator(Token.Operator.Type.LeftShiftAssign)
                } else Token.Operator(Token.Operator.Type.LeftShift)
            }
            else -> Token.Operator(Token.Operator.Type.Lt)
        }
    }

    private suspend inline fun scanMinus(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.MinusAssign)
            }
            '-' -> {
                next()
                Token.Operator(Token.Operator.Type.MinusMinus)
            }
            else -> Token.Operator(Token.Operator.Type.Minus)
        }
    }

    private suspend inline fun scanPlus(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.PlusAssign)
            }
            '+' -> {
                next()
                Token.Operator(Token.Operator.Type.PlusPlus)
            }
            else -> Token.Operator(Token.Operator.Type.Plus)
        }
    }

    private suspend inline fun scanMul(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.MulAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mul)
        }
    }

    private suspend inline fun scanNot(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.Neq)
            }
            else -> Token.Operator(Token.Operator.Type.Not)
        }
    }

    private suspend inline fun scanDiv(): Token {
        return when (peek()) {
            '*' -> {
                next()
                scanCommentToken(StringBuilder("/*"))
            }
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.DivAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Div)
        }
    }

    private suspend inline fun scanCommentToken(commentAcc: StringBuilder): Token {
        // At the start, we're on the first '*' of "/* myComment */"
        var c = next()
        commentAcc.append(c)

        // not exactly the automat but no need for recursion that way.
        while (!(c == '*' && peek() == '/')) {
            if (c == '/' && peek() == '*') printWarning("nested comments are not supported")
            c = next()
            if (c == BufferedInputProvider.END_OF_FILE) return Token.ErrorToken("", "reached EOF while parsing comment.")
            commentAcc.append(c)
        }
        // Not yet end of comment token, we're on the latter '*' of "/* myComment */"),
        // so we advance, safely assuming next() to be '/'
        commentAcc.append(next())
        return Token.Comment(commentAcc.toString())
    }

    private fun printWarning(message: String) {
        if (printWarnings) {
            System.err.apply {
                println("[warning] $message")
                println("  in $position")
            }
        }
    }
}
