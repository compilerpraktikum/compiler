package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import java.lang.StringBuilder

/**
 * lexicographic analysis and tokenization of an input stream.
 *
 * @param input input abstracted in a ring buffer
 * @param stringTable string table of current compilation run
 */
class Lexer(private val input: RingBuffer, private val stringTable: StringTable
) {
    companion object {
        val keywordTokenValues: HashSet<String> = Token.Key.values().map { tokenKey -> tokenKey.repr }.toHashSet()
        val keywordTokenMap: Map<String, Token.Key> = Token.Key.values().map { token -> Pair<String, Token.Key>(token.repr, token)}.toMap()
    }
    
    fun tokenStream() = flow {
        var c = input.nextChar()
        
        while (c != null) {
            when (c) {
                ' ', '\n', '\r', '\t' -> emit(Token.Whitespace())
                '/' -> emit(scanDiv())
                '!' -> emit(scanNot())
                '(' -> emit(Token.Operator(Token.Op.LParen))
                ')' -> emit(Token.Operator(Token.Op.RParen))
                '*' -> emit(scanMul())
                '+' -> emit(scanPlus())
                ',' -> emit(Token.Operator(Token.Op.Comma))
                '-' -> emit(scanMinus())
                '.' -> emit(Token.Operator(Token.Op.Dot))
                ':' -> emit(Token.Operator(Token.Op.Colon))
                ';' -> emit(Token.Operator(Token.Op.Semicolon))
                '<' -> emit(scanLt())
                '=' -> emit(scanAssign())
                '>' -> emit(scanGt())
                '?' -> emit(Token.Operator(Token.Op.QuestionMark))
                '%' -> emit(scanModulo())
                '&' -> emit(scanBitAnd())
                '[' -> emit(Token.Operator(Token.Op.LeftBracket))
                ']' -> emit(Token.Operator(Token.Op.RightBracket))
                '^' -> emit(scanXor())
                '{' -> emit(Token.Operator(Token.Op.LeftBrace))
                '}' -> emit(Token.Operator(Token.Op.RightBrace))
                '~' -> emit(Token.Operator(Token.Op.BitNot))
                '|' -> emit(scanBitOr())
                else -> {
                    when (c) {
                        '0' -> emit(Token.Literal(0))
                        '1', '2', '3', '4', '5', '6', '7', '8', '9' -> emit(scanNonZeroLiteral(c))
                        else -> emit(scanIdent(c))
                    }
                }
            }
            c = input.nextChar()
        }
        
        emit(Token.Eof())
    }.buffer()
    
    private suspend inline fun scanNonZeroLiteral(startDigit: Char): Token {
        val literalBuilder: StringBuilder = StringBuilder().append(startDigit)
        while (Regex("[0-9]").matches(input.peek().toString()) ) {
            literalBuilder.append(input.nextChar())
        }
        return Token.Literal(literalBuilder.toString().toInt())
    }
    
    private suspend inline fun scanIdent(startChar: Char): Token {
        
        val identBuilder: StringBuilder = StringBuilder().append(startChar)
        if (!Regex("[_a-zA-Z]").matches(startChar.toString())) {
            return Token.ErrorToken("unexpected character: $startChar")
        }
        while(Regex("[_a-zA-Z0-9]").matches(input.peek().toString())) {
            identBuilder.append(input.nextChar())
        }
        
        val identifier = identBuilder.toString()
        if (identifier in keywordTokenValues) {
            return Token.Keyword(keywordTokenMap.getValue(identifier))
        }
        return Token.Identifier(identBuilder.toString())
    }
    
    private suspend inline fun scanBitOr(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.OrAssign)
            }
            '|' -> {
                input.nextChar()
                Token.Operator(Token.Op.Or)
            }
            else -> Token.Operator(Token.Op.BitOr)
        }
    }
    
    private suspend inline fun scanBitAnd(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.AndAssign)
            }
            '&' -> {
                input.nextChar()
                Token.Operator(Token.Op.And)
            }
            else -> Token.Operator(Token.Op.BitAnd)
        }
    }
    
    private suspend inline fun scanXor(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.XorAssign)
            }
            else -> Token.Operator(Token.Op.Xor)
        }
    }
    
    private suspend inline fun scanModulo(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.ModAssign)
            }
            else -> Token.Operator(Token.Op.Mod)
        }
    }
    
    private suspend inline fun scanGt(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.GtEq)
            }
            '>' -> {
                input.nextChar()
                when (input.peek()) {
                    '=' -> {
                        input.nextChar()
                        Token.Operator(Token.Op.RightShiftSEAssign)
                    }
                    '>' -> {
                        input.nextChar()
                        if (input.peek() == '=') {
                            input.nextChar()
                            Token.Operator(Token.Op.RightShiftAssign)
                        } else Token.Operator(Token.Op.RightShift)
                    }
                    else -> {
                        Token.Operator(Token.Op.RightShiftSE)
                    }
                }
            }
            else -> Token.Operator(Token.Op.Gt)
        }
    }
    
    private suspend inline fun scanAssign(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.Eq)
            }
            else -> Token.Operator(Token.Op.Assign)
        }
    }
    
    private suspend inline fun scanLt(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.LtEq)
            }
            '<' -> {
                input.nextChar()
                if (input.peek() == '=') {
                    input.nextChar()
                    Token.Operator(Token.Op.LeftShiftAssign)
                } else Token.Operator(Token.Op.LeftShift)
            }
            else -> Token.Operator(Token.Op.Lt)
        }
    }
    
    private suspend inline fun scanMinus(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.MinusAssign)
            }
            '-' -> {
                input.nextChar()
                Token.Operator(Token.Op.MinusMinus)
            }
            else -> Token.Operator(Token.Op.Minus)
        }
    }
    
    private suspend inline fun scanPlus(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.PlusAssign)
            }
            '+' -> {
                input.nextChar()
                Token.Operator(Token.Op.PlusPlus)
            }
            else -> Token.Operator(Token.Op.Plus)
        }
    }
    
    private suspend inline fun scanMul(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.MulAssign)
            }
            else -> Token.Operator(Token.Op.Mul)
        }
    }
    
    private suspend inline fun scanNot(): Token {
        return when (input.peek()) {
            '=' -> {
                input.nextChar()
                Token.Operator(Token.Op.Neq)
            }
            else -> Token.Operator(Token.Op.Not)
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
                Token.Operator(Token.Op.DivAssign)
            }
            else -> Token.Operator(Token.Op.Div)
        }
    }
    
    private suspend inline fun scanCommentToken(commentAcc: StringBuilder): Token {
        // At the start, we're on the first '*' of "/* myComment */"
        var c = input.nextChar()
        commentAcc.append(c)
        
        // not exactly the automat but no need for recursion that way.
        while (!(c == '*' && input.peek() == '/')) {
            c = input.nextChar()
            if (c == null) return Token.ErrorToken("reached EOF while parsing comment.")
            commentAcc.append(c)
        }
        // Not yet end of comment token, we're on the latter '*' of "/* myComment */"),
        // so we advance, safely assuming input.nextChar() to be '/'
        commentAcc.append(input.nextChar())
        return Token.Comment(commentAcc.toString())
    }
    
}