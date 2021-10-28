package edu.kit.compiler

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
    
    data class Operator(val op: Op) : Token()
    
    data class Keyword(val key: Key) : Token()
    
    data class Comment(val content: String) : Token()
    
    data class Whitespace(val content: String) : Token()
    
    object Eof : Token()
    
    class ErrorToken(val error: String) : Token()
    
    enum class Op(val repr: String) {
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
    
    enum class Key(val repr: String) {
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
        True("true"), Try("try"), Void("void"), Volatile("volatile"), While("while")
    }
}

val Iterable<Token>.debugRepr: List<String>
    get() = this.mapNotNull { it.debugRepr }