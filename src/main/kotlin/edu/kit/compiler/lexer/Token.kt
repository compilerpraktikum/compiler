package edu.kit.compiler.lexer

import edu.kit.compiler.source.SourcePosition
import edu.kit.compiler.source.SourceRange
import edu.kit.compiler.source.extend

sealed class Token {
    lateinit var position: SourcePosition

    protected abstract val length: Int
    val range: SourceRange
        get() = position.extend(length)

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

        override val length: Int
            get() = name.text.length
    }

    data class Literal(val value: String) : Token() {
        override val length: Int
            get() = value.length
    }

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

        override val length: Int
            get() = type.repr.length
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

        override val length: Int
            get() = type.repr.length
    }

    data class Comment(val content: String) : Token() {
        override val length: Int
            get() = content.length
    }

    data class Whitespace(val content: String) : Token() {
        override val length: Int
            get() = content.length
    }

    class Eof : Token() {
        override val length: Int
            get() = 1

        override fun toString(): String = "EndOfFile"

        override fun equals(other: Any?): Boolean = other is Eof
        override fun hashCode(): Int = 0
    }

    data class ErrorToken(
        val content: String,
        val error: String?, // null to opt out of automatic error reporting
        val errorReporter: ((ErrorToken) -> Unit)? = null,
    ) : Token() {
        override val length: Int
            get() = content.length
    }
}

fun StringTable.initializeKeywords() {
    Token.Keyword.Type.values().forEach { registerKeyword(it.repr) }
}

val Sequence<Token>.lexTestRepr: Sequence<String>
    get() = this.mapNotNull { it.debugRepr }

val Iterable<Token>.lexTestRepr: List<String>
    get() = this.mapNotNull { it.debugRepr }
