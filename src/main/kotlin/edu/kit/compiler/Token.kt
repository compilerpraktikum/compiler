package edu.kit.compiler

sealed class Token {
    class Identifier(val name: String) : Token()
    class Literal(val value: Int) : Token()
    class Operator(val op: Op) : Token()
    
    class Comment(val content: String) : Token()
    class Whitespace() : Token()
    
    class Eof() : Token()
    
    class ErrorToken(val error: String) : Token()
    
    enum class Op(val repr: String) {
        Neq("!="), Not("!"), LParen("("), RParen(")"), MulAssign("*="), Mul("*"), PlusPlus("++"), PlusAssign("+="), Plus("+"), Comma(","), MinusAssign("-="), MinusMinus("--"), Minus("-"), Dot("."), DivEq("/="), Div("/"), Colon(":"), Semicolon(";"), LeftShiftAssign("<<="), LeftShift("<<"), LEq("<="), Lt("<"), Eq("=="), Assign("="), GtEq(">="), RightShiftAssign(">>="), RightSiftRotAssign(">>>="), RightShiftRot(">>>"), RightShift(">>"), Gt(">"), QuestionMark("?"), ModEq("%="), Mod("%"), AndAssign("&="), And("&&"), BitAnd("&"), LeftBracket("["), RightBracket("]"), XorEq("^="), Xor("^"), LeftBrace("{"), RightBrace("}"), BitNot("~"), OrAssign("|="), Or("||"), BitOr("|")
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