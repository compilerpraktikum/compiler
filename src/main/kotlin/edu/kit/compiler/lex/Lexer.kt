package edu.kit.compiler.lex

import edu.kit.compiler.Token

private fun Char.isIdentifierStartChar() = when (this) {
    in 'a'..'z' -> true
    in 'A'..'Z' -> true
    '_' -> true
    else -> false
}

private fun Char.isIdentifierRestChar() = when (this) {
    in 'a'..'z' -> true
    in 'A'..'Z' -> true
    in '0'..'9' -> true
    '_' -> true
    else -> false
}

private fun Char.isNumberLiteralChar() = this in '0'..'9'

private fun Char.isWhitespace() = when (this) {
    ' ', '\n', '\r', '\t' -> true
    else -> false
}

/**
 * lexicographic analysis and tokenization of the given source file.
 *
 * @param sourceFile input source file
 * @param stringTable string table of current compilation run
 */
@Suppress("BlockingMethodInNonBlockingContext")
class Lexer(
    sourceFile: SourceFile,
    stringTable: StringTable,
) : AbstractLexer(sourceFile, stringTable) {

    override fun scanToken(firstChar: Char): Token = when (firstChar) {
        ' ', '\n', '\r', '\t' -> scanWhitespace(firstChar)
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
        '1', '2', '3', '4', '5', '6', '7', '8', '9' -> scanNonZeroLiteral(firstChar)
        else -> scanIdent(firstChar)
    }

    private fun scanWhitespace(startChar: Char): Token {
        return Token.Whitespace(
            buildString {
                append(startChar)

                while (peek().isWhitespace()) {
                    append(next())
                }
            }
        )
    }

    private fun scanNonZeroLiteral(startDigit: Char): Token {
        return Token.Literal(
            buildString {
                append(startDigit)
                while (peek().isNumberLiteralChar()) {
                    append(next())
                }
            }
        )
    }

    private fun scanIdent(startChar: Char): Token {
        val identBuilder: StringBuilder = StringBuilder().append(startChar)
        if (!startChar.isIdentifierStartChar()) {
            return Token.ErrorToken(startChar.toString(), "unexpected character: $startChar")
        }
        while (peek().isIdentifierRestChar()) {
            identBuilder.append(next())
        }

        val symbol = stringTable.tryRegisterIdentifier(identBuilder.toString())

        return if (symbol.isKeyword) {
            Token.Keyword(Token.Keyword.Type.from(symbol.text)!!)
        } else {
            Token.Identifier(symbol)
        }
    }

    private fun scanBitOr(): Token {
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

    private fun scanBitAnd(): Token {
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

    private fun scanXor(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.XorAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Xor)
        }
    }

    private fun scanModulo(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.ModAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mod)
        }
    }

    private fun scanGt(): Token {
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

    private fun scanAssign(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.Eq)
            }
            else -> Token.Operator(Token.Operator.Type.Assign)
        }
    }

    private fun scanLt(): Token {
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

    private fun scanMinus(): Token {
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

    private fun scanPlus(): Token {
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

    private fun scanMul(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.MulAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mul)
        }
    }

    private fun scanNot(): Token {
        return when (peek()) {
            '=' -> {
                next()
                Token.Operator(Token.Operator.Type.NoEq)
            }
            else -> Token.Operator(Token.Operator.Type.Not)
        }
    }

    private fun scanDiv(): Token {
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

    private fun scanCommentToken(commentAcc: StringBuilder): Token {
        // At the start, we're on the first '*' of "/* myComment */"
        var c = next()
        commentAcc.append(c)

        // not exactly the automaton but no need for recursion that way.
        while (!(c == '*' && peek() == '/')) {
            if (c == '/' && peek() == '*') sourceFile.annotate(AnnotationType.WARNING, sourceFile.currentPosition.extend(2), "nested comments are not supported")
            c = next()
            if (c == InputProvider.END_OF_FILE) {
                val eofPosition = sourceFile.currentPosition
                // delay message creation, because the token.position is injected after this method returns
                return Token.ErrorToken(commentAcc.toString(), null) { token ->
                    sourceFile.annotate(
                        AnnotationType.ERROR,
                        eofPosition,
                        "reached EOF while parsing comment",
                        listOf(SourceNote(token.position.extend(2), "in comment starting here"))
                    )
                }
            }
            commentAcc.append(c)
        }
        // Not yet end of comment token, we're on the latter '*' of "/* myComment */"),
        // so we advance, safely assuming next() to be '/'
        commentAcc.append(next())
        return Token.Comment(commentAcc.toString())
    }
}
