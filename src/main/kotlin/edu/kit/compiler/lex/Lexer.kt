package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow

/**
 * lexicographic analysis and tokenization of an input stream.
 *
 * @param input input abstracted in a ring buffer
 * @param stringTable string table of current compilation run
 */
class Lexer(private val input: InputProvider, private val stringTable: StringTable, private val printWarnings: Boolean = true
) {
    companion object {
        val identFirstCharRegex: Regex = Regex("[_a-zA-Z]")
        val identRestCharsRegex: Regex = Regex("[_a-zA-Z0-9]")
        val literalCharRegex: Regex = Regex("[0-9]")
        val whitespaceCharRegex: Regex = Regex("[ \n\r\t]")
    }
    
    fun tokenStream() = flow {
        var c = input.nextChar()
        
        while (c != null) {
            when (c) {
                ' ', '\n', '\r', '\t' -> emit(scanWhitespace(c))
                '/' -> emit(scanDiv())
                '!' -> emit(scanNot())
                '(' -> emit(Token.Operator(Token.Operator.Type.LParen))
                ')' -> emit(Token.Operator(Token.Operator.Type.RParen))
                '*' -> emit(scanMul())
                '+' -> emit(scanPlus())
                ',' -> emit(Token.Operator(Token.Operator.Type.Comma))
                '-' -> emit(scanMinus())
                '.' -> emit(Token.Operator(Token.Operator.Type.Dot))
                ':' -> emit(Token.Operator(Token.Operator.Type.Colon))
                ';' -> emit(Token.Operator(Token.Operator.Type.Semicolon))
                '<' -> emit(scanLt())
                '=' -> emit(scanAssign())
                '>' -> emit(scanGt())
                '?' -> emit(Token.Operator(Token.Operator.Type.QuestionMark))
                '%' -> emit(scanModulo())
                '&' -> emit(scanBitAnd())
                '[' -> emit(Token.Operator(Token.Operator.Type.LeftBracket))
                ']' -> emit(Token.Operator(Token.Operator.Type.RightBracket))
                '^' -> emit(scanXor())
                '{' -> emit(Token.Operator(Token.Operator.Type.LeftBrace))
                '}' -> emit(Token.Operator(Token.Operator.Type.RightBrace))
                '~' -> emit(Token.Operator(Token.Operator.Type.BitNot))
                '|' -> emit(scanBitOr())
                '0' -> emit(Token.Literal(0))
                '1', '2', '3', '4', '5', '6', '7', '8', '9' -> emit(scanNonZeroLiteral(c))
                else -> emit(scanIdent(c))
            }
            c = input.nextChar()
        }
    
        emit(Token.Eof)
    }.buffer()
    
    private suspend inline fun scanWhitespace(startChar: Char): Token {
        return Token.Whitespace(buildString {
            append(startChar)
    
            while (whitespaceCharRegex.matches(input.peek().toString())) {
                append(input.nextChar())
            }
        })
    }
    
    private suspend inline fun scanNonZeroLiteral(startDigit: Char): Token {
        return Token.Literal(buildString {
            append(startDigit)
            while (literalCharRegex.matches(input.peek().toString()) ) {
                append(input.nextChar())
            }
        }.toInt())
    }
    
    private suspend inline fun scanIdent(startChar: Char): Token {
        val identBuilder: StringBuilder = StringBuilder().append(startChar)
        if (!identFirstCharRegex.matches(startChar.toString())) {
            return Token.ErrorToken("unexpected character: $startChar")
        }
        while (identRestCharsRegex.matches(input.peek().toString())) {
            identBuilder.append(input.nextChar())
        }
    
        val (identifier, stringTableEntry) = stringTable.tryRegisterIdentifier(identBuilder.toString())
    
        return if (stringTableEntry.isKeyword) {
            Token.Keyword(Token.Keyword.Type.from(identifier)!!)
        } else {
            Token.Identifier(identifier)
        }
    }
    
    private suspend inline fun scanBitOr(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.OrAssign)
            }
            '|' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.Or)
            }
            else -> Token.Operator(Token.Operator.Type.BitOr)
        }
    }
    
    private suspend inline fun scanBitAnd(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.AndAssign)
            }
            '&' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.And)
            }
            else -> Token.Operator(Token.Operator.Type.BitAnd)
        }
    }
    
    private suspend inline fun scanXor(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.XorAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Xor)
        }
    }
    
    private suspend inline fun scanModulo(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.ModAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mod)
        }
    }
    
    private suspend inline fun scanGt(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.GtEq)
            }
            '>' -> {
                input.nextChar()
                when (input.peek()) {
                    '=' -> {
                        input.nextChar()
                        Token.Operator(Token.Operator.Type.RightShiftSEAssign)
                    }
                    '>' -> {
                        input.nextChar()
                        if (input.peek() == '=') {
                            input.nextChar()
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
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.Eq)
            }
            else -> Token.Operator(Token.Operator.Type.Assign)
        }
    }
    
    private suspend inline fun scanLt(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.LtEq)
            }
            '<' -> {
                input.nextChar()
                if (input.peek() == '=') {
                    input.nextChar()
                    Token.Operator(Token.Operator.Type.LeftShiftAssign)
                } else Token.Operator(Token.Operator.Type.LeftShift)
            }
            else -> Token.Operator(Token.Operator.Type.Lt)
        }
    }
    
    private suspend inline fun scanMinus(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.MinusAssign)
            }
            '-' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.MinusMinus)
            }
            else -> Token.Operator(Token.Operator.Type.Minus)
        }
    }
    
    private suspend inline fun scanPlus(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.PlusAssign)
            }
            '+' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.PlusPlus)
            }
            else -> Token.Operator(Token.Operator.Type.Plus)
        }
    }
    
    private suspend inline fun scanMul(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.MulAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Mul)
        }
    }
    
    private suspend inline fun scanNot(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.Neq)
            }
            else -> Token.Operator(Token.Operator.Type.Not)
        }
    }
    
    private suspend inline fun scanDiv(): Token {
        return when (input.peek()) {
            '*' -> {
                input.nextChar()
                scanCommentToken(StringBuilder("/*"))
            }
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Operator.Type.DivAssign)
            }
            else -> Token.Operator(Token.Operator.Type.Div)
        }
    }
    
    private suspend inline fun scanCommentToken(commentAcc: StringBuilder): Token {
        // At the start, we're on the first '*' of "/* myComment */"
        var c = input.nextChar()
        commentAcc.append(c)
        
        // not exactly the automat but no need for recursion that way.
        while (!(c == '*' && input.peek() == '/')) {
            if (c == '/' && input.peek() == '*') printWarning("No nested comments!")
            c = input.nextChar()
            if (c == null) return Token.ErrorToken("reached EOF while parsing comment.")
            commentAcc.append(c)
        }
        // Not yet end of comment token, we're on the latter '*' of "/* myComment */"),
        // so we advance, safely assuming input.nextChar() to be '/'
        commentAcc.append(input.nextChar())
        return Token.Comment(commentAcc.toString())
    }
    
    private fun printWarning(message: String) {
        if (printWarnings) System.err.println("[Warning]: $message")
    }
    
}
