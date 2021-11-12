package edu.kit.compiler

import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol

sealed class Token {
    lateinit var position: SourcePosition

    val debugRepr: String?
        get() =
            when (this) {
                is Identifier -> "identifier ${name.text}"
                is Literal -> "integer literal $value"
                is Operator -> type.repr
                is Keyword -> type.repr
                is Comment -> null
                is Whitespace -> null
                is Eof -> "EOF"
                is ErrorToken -> "error $error"
            }

    data class Identifier(val name: Symbol) : Token() {
        companion object {
            /**
             * Identifier to use as a placeholder (e.g. for anchor sets). Do **NOT** use for the real AST as the [Symbol]
             * stored in [Identifier.name] is invalid.
             */
            val Placeholder = Identifier(Symbol("", isKeyword = false))
        }
    }

    data class Literal(val value: String) : Token()

    data class Operator(val type: Type) : Token() {

        enum class Type(val repr: String) {
            NoEq("!="),
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

    data class Keyword(val type: Type) : Token() {

        @Suppress("unused")
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

    object Eof : Token() {
        override fun toString(): String = "EndOfFile"
    }

    data class ErrorToken(val content: String, val error: String) : Token()
}

fun StringTable.initializeKeywords() {
    Token.Keyword.Type.values().forEach { registerKeyword(it.repr) }
}

val Sequence<Token>.lexTestRepr: Sequence<String>
    get() = this.mapNotNull { it.debugRepr }

val Iterable<Token>.lexTestRepr: List<String>
    get() = this.mapNotNull { it.debugRepr }
