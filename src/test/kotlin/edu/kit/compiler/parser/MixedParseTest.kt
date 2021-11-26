package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.astOf
import edu.kit.compiler.ast.wrapMockValid
import edu.kit.compiler.utils.TestUtils.expectNode
import edu.kit.compiler.utils.toSymbol
import edu.kit.compiler.wrapper.wrappers.Parsed
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

@ExperimentalStdlibApi
internal class MixedParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private val validEmptyBlock = AST.Block(listOf()).wrapMockValid()

    private val validEmptyBlockStatement = AST.Block(
        listOf()
    ).wrapBlockStatement().wrapMockValid()

    private fun expectAst(input: String, expectedAST: List<Parsed<AST.ClassDeclaration>>) =
        expectNode(
            input,
            expectedAST
        ) { sourceRangeWiper ->
            parseClassDeclarations(emptyAnchorSet).map { s ->
                sourceRangeWiper.visitClassDeclaration(
                    s
                )
            }
        }

    @Test
    fun testParseEmptyBlock() =
        expectNode("{}", validEmptyBlock) { it.visitBlock(parseBlock(emptyAnchorSet)) }

    @Test
    fun testParseBlockOfEmptyBlocks() =
        expectNode(
            "{{{}}}",
            AST.Block(
                listOf(
                    AST.Block(
                        listOf(
                            validEmptyBlockStatement
                        )
                    ).wrapBlockStatement().wrapMockValid()
                )
            )
                .wrapMockValid()
        ) { it.visitBlock(parseBlock(emptyAnchorSet)) }

    @Test
    fun testParseBlockWithEmptyStatement() =
        expectNode("{;}", AST.Block(listOf(validEmptyBlockStatement)).wrapMockValid()) { it.visitBlock(parseBlock(emptyAnchorSet)) }

    @Test
    fun testParseBlockWithMultipleEmptyStatement() = expectNode(
        "{;;;;}",
        AST.Block(
            listOf(
                validEmptyBlockStatement,
                validEmptyBlockStatement,
                validEmptyBlockStatement,
                validEmptyBlockStatement,
            )
        ).wrapMockValid()
    ) { it.visitBlock(parseBlock(emptyAnchorSet)) }

    @Test
    fun testDisambiguateVarDeclarationAndExpression() = expectNode(
        "{ myident; mytype myident2; }",
        AST.Block(
            listOf(
                AST.ExpressionStatement(AST.IdentifierExpression("myident".toSymbol().wrapMockValid()).wrapMockValid())
                    .wrapBlockStatement()
                    .wrapMockValid(),
                AST.LocalVariableDeclarationStatement(
                    "myident2".toSymbol().wrapMockValid(),
                    Type.Class("mytype".toSymbol().wrapMockValid()).wrapMockValid(),
                    null
                )
                    .wrapMockValid()
            )
        ).wrapMockValid()
    ) { it.visitBlock(parseBlock(emptyAnchorSet)) }

    @Test
    fun testParseAssignment() = expectNode(
        "myIdent = 3;",
        AST.ExpressionStatement(
            AST.BinaryExpression(
                AST.IdentifierExpression("myIdent".toSymbol().wrapMockValid()).wrapMockValid(),
                AST.LiteralExpression.Integer("3").wrapMockValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapMockValid()
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseReturn() = expectNode(
        "return;",
        AST.ReturnStatement(null).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseReturnValue() = expectNode(
        "return(2);",
        AST.ReturnStatement(AST.LiteralExpression.Integer("2").wrapMockValid()).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseBasicWhile() = expectNode(
        "while(2) {};",
        AST.WhileStatement(
            AST.LiteralExpression.Integer("2").wrapMockValid(),
            validEmptyBlock
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseBasicIf() = expectNode(
        "if(2) {};",
        AST.IfStatement(
            AST.LiteralExpression.Integer("2").wrapMockValid(),
            validEmptyBlock,
            null
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseBasicIfElse() = expectNode(
        "if(2) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression.Integer("2").wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseBasicIfElse_bool() = expectNode(
        "if(true) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression.Boolean(true).wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

    @Test
    fun testParseBasicIfElse_ident() = expectNode(
        "if(myIdent) {} else {};",
        AST.IfStatement(
            AST.IdentifierExpression("myIdent".toSymbol().wrapMockValid()).wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
    ) { it.visitStatement(parseStatement(emptyAnchorSet)) }

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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    Type.Array(
                                        Type.Array(
                                            Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    ).wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "i".toSymbol().wrapMockValid(),
                                        Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        Type.Integer.wrapMockValid(),
                                        AST.BinaryExpression(
                                            AST.IdentifierExpression("i".toSymbol().wrapMockValid()).wrapMockValid(),
                                            AST.UnaryExpression(
                                                AST.IdentifierExpression("i".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.UnaryExpression.Operation.MINUS
                                            ).wrapMockValid(),
                                            AST.BinaryExpression.Operation.ADDITION
                                        ).wrapMockValid()
                                    ).wrapMockValid(),
                                )
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()
    ) { it.visitProgram(parse()) }

    @Test
    fun debugParserMJTest_4() {
        expectNode(
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
                        "_Klasse".toSymbol().wrapMockValid(),
                        listOf(
                            AST.MainMethod(
                                "main".toSymbol().wrapMockValid(), Type.Void.wrapMockValid(),
                                listOf(
                                    AST.Parameter(
                                        "args".toSymbol().wrapMockValid(),
                                        Type.Array(
                                            Type.Array(
                                                Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                            ).wrapMockValid()
                                        )
                                            .wrapMockValid()
                                    ).wrapMockValid()
                                ),
                                AST.Block(
                                    listOf(
                                        AST.IfStatement(
                                            AST.FieldAccessExpression(
                                                AST.LiteralExpression.Null.wrapMockValid(),
                                                "nothing".toSymbol().wrapMockValid()
                                            )
                                                .wrapMockValid(),
                                            AST.IfStatement(
                                                AST.MethodInvocationExpression(
                                                    AST.LiteralExpression.Boolean(true).wrapMockValid(),
                                                    "fun".toSymbol().wrapMockValid(),
                                                    emptyList()
                                                ).wrapMockValid(),
                                                AST.IfStatement(
                                                    AST.ArrayAccessExpression(
                                                        AST.LiteralExpression.Boolean(false).wrapMockValid(),
                                                        AST.LiteralExpression.Integer("472183921789789798798798798798787789738120391203213213")
                                                            .wrapMockValid()
                                                    ).wrapMockValid(),
                                                    AST.ReturnStatement(null).wrapMockValid(),
                                                    null
                                                ).wrapMockValid(),
                                                null
                                            ).wrapMockValid(),
                                            null
                                        ).wrapBlockStatement().wrapMockValid()
                                    )
                                ).wrapMockValid()
                            ).wrapMockValid()
                        )
                    ).wrapMockValid()
                )
            ).wrapMockValid()

        ) { it.visitProgram(parse()) }
    }

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
                    "Test".toSymbol().wrapMockValid(),
                    listOf(
                        AST.Method(
                            "m".toSymbol().wrapMockValid(), Type.Void.wrapMockValid(),
                            listOf(),
                            AST.Block(
                                listOf(
                                    AST.ExpressionStatement(
                                        AST.ArrayAccessExpression(
                                            AST.ArrayAccessExpression(
                                                AST.IdentifierExpression("a".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.BinaryExpression(
                                                    AST.LiteralExpression.Integer("2").wrapMockValid(),
                                                    AST.BinaryExpression(
                                                        AST.UnaryExpression(
                                                            AST.IdentifierExpression("i".toSymbol().wrapMockValid())
                                                                .wrapMockValid(),
                                                            AST.UnaryExpression.Operation.MINUS
                                                        ).wrapMockValid(),
                                                        AST.LiteralExpression.Integer("1").wrapMockValid(),
                                                        AST.BinaryExpression.Operation.ADDITION
                                                    ).wrapMockValid(),
                                                    AST.BinaryExpression.Operation.MULTIPLICATION
                                                ).wrapMockValid()
                                            ).wrapMockValid(),
                                            AST.LiteralExpression.Integer("2").wrapMockValid()
                                        ).wrapMockValid()
                                    ).wrapBlockStatement().wrapMockValid()
                                )
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()
    ) { it.visitProgram(parse()) }

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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    Type.Array(
                                        Type.Array(
                                            Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    )
                                        .wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression.Boolean(true).wrapMockValid(),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.LiteralExpression.Integer("3").wrapMockValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapMockValid()
                                        ).wrapMockValid(),
                                        null
                                    ).wrapBlockStatement().wrapMockValid()
                                )
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()

    ) { it.visitProgram(parse()) }

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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    Type.Array(
                                        Type.Array(
                                            Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    )
                                        .wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression.Boolean(true).wrapMockValid(),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.LiteralExpression.Integer("3").wrapMockValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapMockValid()
                                        ).wrapMockValid(),
                                        null
                                    ).wrapBlockStatement().wrapMockValid()
                                )
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()

    ) { it.visitProgram(parse()) }

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
                    field("myArray", Type.Array(Type.Array(Type.Boolean.wrapMockValid()).wrapMockValid()))
                    field("myArray2", Type.Array(Type.Void.wrapMockValid()))
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
                        param("ident2", Type.Class("myClass".toSymbol().wrapMockValid()))
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
                            "arr".toSymbol().wrapMockValid(),
                            Type.Array(
                                Type.Array(Type.Class("Strig".toSymbol().wrapMockValid()).wrapMockValid())
                                    .wrapMockValid()
                            )
                                .wrapMockValid()
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
                    mainMethod(
                        "main",
                        Type.Void,
                        AST.Parameter(
                            "args".toSymbol().wrapMockValid(),
                            Type.Array(Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid())
                                .wrapMockValid()
                        )
                    ) {
                        localDeclaration(
                            "abc",
                            Type.Array(Type.Array(Type.Integer.wrapMockValid()).wrapMockValid())
                        ) {
                            newArrayOf(
                                Type.Array(
                                    Type.Array(Type.Integer.wrapMockValid()).wrapMockValid()
                                )
                            ) {
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
                    mainMethod(
                        "main",
                        Type.Void,
                        AST.Parameter(
                            "args".toSymbol().wrapMockValid(),
                            Type.Array(Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid())
                                .wrapMockValid()
                        )
                    ) {
                        localDeclaration(
                            "abc",
                            Type.Array(
                                Type.Array(
                                    Type.Array(Type.Class("SomeClass".toSymbol().wrapMockValid()).wrapMockValid())
                                        .wrapMockValid()
                                ).wrapMockValid()
                            )
                        ) {
                            newArrayOf(
                                Type.Array(
                                    Type.Array(
                                        Type.Array(
                                            Type.Class("SomeClass".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    ).wrapMockValid()
                                )
                            ) {
                                literal(
                                    "22"
                                )
                            }
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
                            "args".toSymbol().wrapMockValid(),
                            Type.Array(Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid())
                                .wrapMockValid()
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
