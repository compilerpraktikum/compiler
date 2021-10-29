package edu.kit.compiler

import edu.kit.compiler.lex.StringTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

sealed class Token {
    
    val debugRepr: String?
        get() =
            when (this) {
                is Comment -> null
                is Eof -> "EOF"
                is ErrorToken -> "error $error"
                is Identifier -> "identifier $name"
                is Keyword -> key.repr
                is Literal -> "integer literal $value"
                is Operator -> op.repr
                is Whitespace -> null
            }
    
    data class Identifier(val name: String) : Token()
    
    data class Literal(val value: Int) : Token()
    
    data class Operator(val op: Type) : Token() {
        
        enum class Type(val repr: String) {
            Neq("!="),
            Not("!"),
            LParen("("),
            RParen(")"),
            MulAssign("*="),
            Mul("*"),
            PlusPlus("++"),
            PlusAssign("+="),
            Plus("+"),
            Comma(","),
            MinusAssign("-="),
            MinusMinus("--"),
            Minus("-"),
            Dot("."),
            DivAssign("/="),
            Div("/"),
            Colon(":"),
            Semicolon(";"),
            LeftShiftAssign("<<="),
            LeftShift("<<"),
            LtEq("<="),
            Lt("<"),
            Eq("=="),
            Assign("="),
            GtEq(">="),
            RightShiftSEAssign(">>="),
            RightShiftAssign(">>>="),
            RightShift(">>>"),
            RightShiftSE(">>"),
            Gt(">"),
            QuestionMark("?"),
            ModAssign("%="),
            Mod("%"),
            AndAssign("&="),
            And("&&"),
            BitAnd("&"),
            LeftBracket("["),
            RightBracket("]"),
            XorAssign("^="),
            Xor("^"),
            LeftBrace("{"),
            RightBrace("}"),
            BitNot("~"),
            OrAssign("|="),
            Or("||"),
            BitOr("|")
        }
        
    }
    
    data class Keyword(val key: Type) : Token() {
        
        enum class Type(val repr: String) {
            Abstract("abstract"), Assert("assert"), Boolean("boolean"), Break("break"),
            Byte("byte"), Case("case"), Catch("catch"), Char("char"), Class("class"),
            Const("const"), Continue("continue"), Default("default"), Double("double"),
            Do("do"), Else("else"), Enum("enum"), Extends("extends"), False("false"),
            Finally("finally"), Final("final"), Float("float"), For("for"), Goto("goto"),
            If("if"), Implements("implements"), Import("import"), InstanceOf("instanceof"),
            Interface("interface"), Int("int"), Long("long"), Native("native"), New("new"),
            Null("null"), Package("package"), Private("private"), Protected("protected"),
            Public("public"), Return("return"), Short("short"), Static("static"),
            StrictFp("strictfp"), Super("super"), Switch("switch"), Synchronized("synchronized"),
            This("this"), Throws("throws"), Throw("throw"), Transient("transient"),
            True("true"), Try("try"), Void("void"), Volatile("volatile"), While("while");
            
            companion object {
                private val reprToType = values().associateBy { token -> token.repr }
                
                fun from(repr: String): Type? {
                    return reprToType[repr]
                }
            }
        }
        
    }
    
    data class Comment(val content: String) : Token()
    
    data class Whitespace(val content: String) : Token()
    
    object Eof : Token()
    
    data class ErrorToken(val error: String) : Token()
    
}

fun StringTable.initializeKeywords() {
    Token.Keyword.Type.values().forEach { registerKeyword(it.repr) }
}

val Flow<Token>.lexTestRepr: Flow<String>
    get() = this.mapNotNull { it.debugRepr }

// yeah.. I know. Ugly, but they Flow and Iterable don't share an interface
val Iterable<Token>.lexTestRepr: List<String>
    get() = this.mapNotNull { it.debugRepr }
