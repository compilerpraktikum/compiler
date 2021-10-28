package edu.kit.compiler

sealed class Token {
    class Identifier(val name: String) : Token() {
        override fun toString(): String = "identifier $name"
    }
    
    class Literal(val value: Int) : Token() {
        override fun toString(): String = "integer literal $value"
    }
    
    class Operator(val op: Op) : Token() {
        override fun toString(): String = op.repr
    }
    class Keyword(val key: Key) : Token() {
        override fun toString(): String = key.repr
    }
    
    class Comment(val content: String) : Token() {
        override fun toString(): String = this.content
    }
    class Whitespace() : Token()
    
    class Eof() : Token() {
        override fun toString(): String = "EOF"
    }
    
    class ErrorToken(val error: String) : Token()
    
    enum class Op(val repr: String) {
        Neq("!="), Not("!"), LParen("("), RParen(")"), MulAssign("*="), Mul("*"), PlusPlus("++"), PlusAssign("+="), Plus("+"), Comma(","), MinusAssign("-="), MinusMinus("--"), Minus("-"), Dot("."), DivAssign("/="), Div("/"), Colon(":"), Semicolon(";"), LeftShiftAssign("<<="), LeftShift("<<"), LtEq("<="), Lt("<"), Eq("=="), Assign("="), GtEq(">="), RightShiftSEAssign(">>="), RightShiftAssign(">>>="), RightShift(">>>"), RightShiftSE(">>"), Gt(">"), QuestionMark("?"), ModAssign("%="), Mod("%"), AndAssign("&="), And("&&"), BitAnd("&"), LeftBracket("["), RightBracket("]"), XorAssign("^="), Xor("^"), LeftBrace("{"), RightBrace("}"), BitNot("~"), OrAssign("|="), Or("||"), BitOr("|")
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