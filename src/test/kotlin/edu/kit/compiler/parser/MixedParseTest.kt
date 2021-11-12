package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Lenient
import edu.kit.compiler.ast.Of
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.astOf
import edu.kit.compiler.ast.wrapValid
import edu.kit.compiler.utils.TestUtils.expectNode
import edu.kit.compiler.utils.toSymbol
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

@ExperimentalStdlibApi
internal class MixedParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private val validEmptyBlock = AST.Block<Lenient<Of>, Lenient<Of>>(
        listOf()
    ).wrapValid()

    private fun expectAst(input: String, expectedAST: List<Lenient<AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>>>>) =
        expectNode(input, expectedAST) { parseClassDeclarations(emptyAnchorSet) }

    @Test
    fun testParseEmptyBlock() =
        expectNode("{}", validEmptyBlock) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseBlockOfEmptyBlocks() =
        expectNode(
            "{{{}}}",
            AST.Block<Lenient<Of>, Lenient<Of>>(
                listOf(
                    AST.Block<Lenient<Of>, Lenient<Of>>(
                        listOf(
                            validEmptyBlock
                        )
                    ).wrapValid()
                )
            )
                .wrapValid()
        ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseBlockWithEmptyStatement() =
        expectNode("{;}", AST.Block(listOf(AST.EmptyStatement.wrapValid())).wrapValid()) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseBlockWithMultipleEmptyStatement() = expectNode(
        "{;;;;}",
        AST.Block(
            listOf(
                AST.EmptyStatement,
                AST.EmptyStatement,
                AST.EmptyStatement,
                AST.EmptyStatement
            ).map { it.wrapValid() }
        ).wrapValid()
    ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testDisambiguateVarDeclarationAndExpression() = expectNode(
        "{ myident; mytype myident2; }",
        AST.Block(
            listOf(
                AST.ExpressionStatement(AST.IdentifierExpression("myident".toSymbol()).wrapValid()).wrapValid(),
                AST.LocalVariableDeclarationStatement<Lenient<Of>>("myident2".toSymbol(), Type.ClassType("mytype".toSymbol()), null)
                    .wrapValid()
            )
        ).wrapValid()
    ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseAssignment() = expectNode(
        "myIdent = 3;",
        AST.ExpressionStatement(
            AST.BinaryExpression(
                AST.IdentifierExpression("myIdent".toSymbol()).wrapValid(),
                AST.LiteralExpression("3").wrapValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapValid()
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseReturn() = expectNode(
        "return;",
        AST.ReturnStatement<Lenient<Of>>(null).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseReturnValue() = expectNode(
        "return(2);",
        AST.ReturnStatement(AST.LiteralExpression("2").wrapValid()).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicWhile() = expectNode(
        "while(2) {};",
        AST.WhileStatement(
            AST.LiteralExpression("2").wrapValid(),
            validEmptyBlock
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIf() = expectNode(
        "if(2) {};",
        AST.IfStatement(
            AST.LiteralExpression("2").wrapValid(),
            validEmptyBlock,
            null
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse() = expectNode(
        "if(2) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression("2").wrapValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse_bool() = expectNode(
        "if(true) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression(true).wrapValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse_ident() = expectNode(
        "if(myIdent) {} else {};",
        AST.IfStatement(
            AST.IdentifierExpression("myIdent".toSymbol()).wrapValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun debugParserMJTest_2() = expectNode(
        """
        /* OK , unary minus after binop */

        class Main {
            public static void main(String[] args) {
                int i;

                int x = i + -i;
            }
        }""",
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "Main".toSymbol(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol(), Type.Void,
                            listOf(AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement<Lenient<Of>>("i".toSymbol(), Type.Integer, null)
                                        .wrapValid(),
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol(),
                                        Type.Integer,
                                        AST.BinaryExpression(
                                            AST.IdentifierExpression("i".toSymbol()).wrapValid(),
                                            AST.UnaryExpression(
                                                AST.IdentifierExpression("i".toSymbol()).wrapValid(),
                                                AST.UnaryExpression.Operation.MINUS
                                            ).wrapValid(),
                                            AST.BinaryExpression.Operation.ADDITION
                                        ).wrapValid()
                                    ).wrapValid(),
                                )
                            ).wrapValid()
                        ).wrapValid()
                    )
                ).wrapValid()
            )
        )
    ) { parse() }

    @Test
    fun debugParserMJTest_4() = expectNode(
        """
            class _Klasse {
                public static void main(String[] args) {
                    if (null.nothing) if (true.fun()) if (false[472183921789789798798798798798787789738120391203213213]) return;
                }
            }
        """,
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "_Klasse".toSymbol(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol(), Type.Void,
                            listOf(AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))),
                            AST.Block(
                                listOf(
                                    AST.IfStatement(
                                        AST.FieldAccessExpression(AST.LiteralExpression("null").wrapValid(), "nothing".toSymbol())
                                            .wrapValid(),
                                        AST.IfStatement(
                                            AST.MethodInvocationExpression(
                                                AST.LiteralExpression(true).wrapValid(),
                                                "fun".toSymbol(),
                                                emptyList()
                                            ).wrapValid(),
                                            AST.IfStatement(
                                                AST.ArrayAccessExpression(
                                                    AST.LiteralExpression(false).wrapValid(),
                                                    AST.LiteralExpression("472183921789789798798798798798787789738120391203213213")
                                                        .wrapValid()
                                                ).wrapValid(),
                                                AST.ReturnStatement<Lenient<Of>>(null).wrapValid(),
                                                null
                                            ).wrapValid(),
                                            null
                                        ).wrapValid(),
                                        null
                                    ).wrapValid()
                                )
                            ).wrapValid()
                        ).wrapValid()
                    )
                ).wrapValid()
            )
        )

    ) { parse() }

    //    @Ignore
    @Test
    fun debugParserMJTest_ArrayAccessValid() = expectNode(
        """class Test {
            public void m() {
                a[2 * (-i + 1)][2];
            }
        }""",
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "Test".toSymbol(),
                    listOf(
                        AST.Method(
                            "m".toSymbol(), Type.Void,
                            listOf(),
                            AST.Block(
                                listOf(
                                    AST.ExpressionStatement(
                                        AST.ArrayAccessExpression(
                                            AST.ArrayAccessExpression(
                                                AST.IdentifierExpression("a".toSymbol()).wrapValid(),
                                                AST.BinaryExpression(
                                                    AST.LiteralExpression("2").wrapValid(),
                                                    AST.BinaryExpression(
                                                        AST.UnaryExpression(
                                                            AST.IdentifierExpression("i".toSymbol()).wrapValid(),
                                                            AST.UnaryExpression.Operation.MINUS
                                                        ).wrapValid(),
                                                        AST.LiteralExpression("1").wrapValid(),
                                                        AST.BinaryExpression.Operation.ADDITION
                                                    ).wrapValid(),
                                                    AST.BinaryExpression.Operation.MULTIPLICATION
                                                ).wrapValid()
                                            ).wrapValid(),
                                            AST.LiteralExpression("2").wrapValid()
                                        ).wrapValid()
                                    ).wrapValid()
                                )
                            ).wrapValid()
                        ).wrapValid()
                    )
                ).wrapValid()
            )
        )
    ) { parse() }

    @Ignore
    @Test
    fun debugParserMJTest_newArrayWithAccess2() = expectNode(
        """/* OK */

        class Main {
            public static void main(String[] args) {
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}
                }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}};
            }
        }""",
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "Main".toSymbol(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol(), Type.Void,
                            listOf(AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement<Lenient<Of>>("x".toSymbol(), Type.Integer, null)
                                        .wrapValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression(true),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol()).wrapValid(),
                                                AST.LiteralExpression("3").wrapValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapValid()
                                        ).wrapValid(),
                                        null
                                    ).wrapValid()
                                )
                            ).wrapValid()
                        ).wrapValid()
                    )
                ).wrapValid()
            )
        ).wrapValid()

    ) { parse() }

    @Test
    fun debugParserMJTest_1() = expectNode(
        """ /* OK */

            class Main {
            public static void main(String[] args) {
                int x;
                if (true) x = 3;
            }
        }""",
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "Main".toSymbol(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol(), Type.Void,
                            listOf(AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement<Lenient<Of>>("x".toSymbol(), Type.Integer, null)
                                        .wrapValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression(true).wrapValid(),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol()).wrapValid(),
                                                AST.LiteralExpression("3").wrapValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapValid()
                                        ).wrapValid(),
                                        null
                                    ).wrapValid()
                                )
                            ).wrapValid()
                        ).wrapValid()
                    )
                ).wrapValid()
            )
        )

    ) { parse() }

    @Test
    fun testBasicBlock() = expectAst(
        "class test { public void test() { } }",
        astOf {
            clazz("test") {
                method("test", Type.Void) {
                }
            }
        }
    )

    @Test
    fun testBlockWithEmptyStatements() = expectAst(
        "class test { public void test() { ;; } }",
        astOf {
            clazz("test") {
                method("test", Type.Void) {
                    emptyStatement()
                    emptyStatement()
                }
            }
        }
    )

    @Test
    fun testEmptyClass() {
        expectAst(
            "class testClass { }",
            astOf {
                clazz("testClass") {}
            }
        )
    }

    @Test
    fun testOneClasTwoFields() {
        expectAst(
            "class testClass { public boolean myIdent; public void myIdent2; }",
            astOf {
                clazz("testClass") {
                    field("myIdent", Type.Boolean)
                    field("myIdent2", Type.Void)
                }
            }
        )
    }

    @Test
    fun testOneClassArrayField() {
        expectAst(
            "class testClass { public boolean [] [] myArray; public void [] myArray2; }",
            astOf {
                clazz("testClass") {
                    field("myArray", Type.Array(Type.Array(Type.Boolean)))
                    field("myArray2", Type.Array(Type.Void))
                }
            }
        )
    }

    @Test
    fun testOneClassMethod() {
        expectAst(
            "class testClass { public void nomain() {} }",
            astOf {
                clazz("testClass") {
                    method("nomain", Type.Void) {}
                }
            }
        )
    }

    @Test
    fun testOneMethodWithParams() {
        expectAst(
            "class testClass { public void nomain(boolean ident, myClass ident2) {} }",
            astOf {
                clazz("testClass") {
                    method(
                        "nomain", Type.Void,
                        param("ident", Type.Boolean),
                        param("ident2", Type.ClassType("myClass".toSymbol()))
                    ) {
                    }
                }
            }
        )
    }

    @Test
    fun testOneMethodOneMainMethod() {
        expectAst(
            "class testClass { public static void mymain(Strig[][] arr ) {} }",
            astOf {
                clazz("testClass") {
                    mainMethod(
                        "mymain", Type.Void,
                        AST.Parameter(
                            "arr".toSymbol(),
                            Type.Array(Type.Array(Type.ClassType("Strig".toSymbol())))
                        )
                    ) {}
                }
            }
        )
    }

    @Test
    fun testPrimitiveArrayExpr() {
        expectAst(
            "class a { public static void main(String[] args) { int[][] abc = new int[22][]; } }",
            astOf {
                clazz("a") {
                    mainMethod("main", Type.Void, AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))) {
                        localDeclaration("abc", Type.Array(Type.Array(Type.Integer))) {
                            newArrayOf(Type.Array(Type.Array(Type.Integer))) {
                                literal("22")
                            }
                        }
                    }
                }
            }
        )
    }

    @Test
    fun testIdentArrayExpr() {
        expectAst(
            "class a { public static void main(String[] args) { SomeClass[][][] abc = new SomeClass[22][][]; } }",
            astOf {
                clazz("a") {
                    mainMethod("main", Type.Void, AST.Parameter("args".toSymbol(), Type.Array(Type.ClassType("String".toSymbol())))) {
                        localDeclaration("abc", Type.Array(Type.Array(Type.Array(Type.ClassType("SomeClass".toSymbol()))))) {
                            newArrayOf(Type.Array(Type.Array(Type.Array(Type.ClassType("SomeClass".toSymbol()))))) { literal("22") }
                        }
                    }
                }
            }
        )
    }

    @Test
    fun testMultiArrayAccess() {
        expectAst(
            "class a { public static void main(String[] args) { a[10 + b]; } }",
            astOf {
                clazz("a") {
                    mainMethod(
                        "main", Type.Void,
                        AST.Parameter(
                            "args".toSymbol(),
                            Type.Array(Type.ClassType("String".toSymbol()))
                        )
                    ) {
                        expressionStatement {
                            arrayAccess({ ident("a") }) {
                                binOp(
                                    AST.BinaryExpression.Operation.ADDITION,
                                    { literal("10") }, { ident("b") }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
