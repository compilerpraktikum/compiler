package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.ILLEGAL_SOURCE_RANGE
import edu.kit.compiler.ast.Parsed
import edu.kit.compiler.ast.astOf
import edu.kit.compiler.ast.wrapMockValid
import edu.kit.compiler.utils.expectNode
import edu.kit.compiler.utils.toSymbol
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

@ExperimentalStdlibApi
internal class MixedParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private val validEmptyBlock = AST.Block(listOf(), Unit.wrapMockValid(), Unit.wrapMockValid()).wrapMockValid()
    private val validEmptyBlockStatement = validEmptyBlock

    private fun expectAst(input: String, expectedAST: List<Parsed<AST.ClassDeclaration>>) =
        expectNode(input, expectedAST) { parseClassDeclarations(emptyAnchorSet) }

    @Test
    fun testParseEmptyBlock() =
        expectNode("{}", validEmptyBlock) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseLeadingZeroLiteralReturn() =
        expectNode(
            "return 02;",
            AST.ReturnStatement(Parsed.Error(ILLEGAL_SOURCE_RANGE, AST.LiteralExpression.Integer("0", false)))
                .wrapMockValid()
        ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseLeadingZeroLiteralExpression() =
        expectNode(
            "02;",
            Parsed.Error(
                ILLEGAL_SOURCE_RANGE,
                AST.ExpressionStatement(AST.LiteralExpression.Integer("0", false).wrapMockValid())
            )
        ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBlockOfEmptyBlocks() =
        expectNode(
            "{{{}}}",
            AST.Block(
                listOf(
                    AST.Block(
                        listOf(
                            validEmptyBlockStatement
                        ),
                        Unit.wrapMockValid(),
                        Unit.wrapMockValid()
                    ).wrapMockValid()
                ),
                Unit.wrapMockValid(),
                Unit.wrapMockValid()
            ).wrapMockValid()
        ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseBlockWithEmptyStatement() =
        expectNode(
            "{;}",
            AST.Block(listOf(validEmptyBlockStatement), Unit.wrapMockValid(), Unit.wrapMockValid()).wrapMockValid()
        ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseBlockWithMultipleEmptyStatement() = expectNode(
        "{;;;;}",
        AST.Block(
            listOf(
                validEmptyBlockStatement,
                validEmptyBlockStatement,
                validEmptyBlockStatement,
                validEmptyBlockStatement,
            ),
            Unit.wrapMockValid(), Unit.wrapMockValid()
        ).wrapMockValid()
    ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testDisambiguateVarDeclarationAndExpression() = expectNode(
        "{ myident; mytype myident2; }",
        AST.Block(
            listOf(
                AST.ExpressionStatement(AST.IdentifierExpression("myident".toSymbol().wrapMockValid()).wrapMockValid())
                    .wrapMockValid(),
                AST.LocalVariableDeclarationStatement(
                    "myident2".toSymbol().wrapMockValid(),
                    AST.Type.Class("mytype".toSymbol().wrapMockValid()).wrapMockValid(),
                    null
                ).wrapMockValid()
            ),
            Unit.wrapMockValid(), Unit.wrapMockValid()
        ).wrapMockValid()
    ) { parseBlock(emptyAnchorSet) }

    @Test
    fun testParseAssignment() = expectNode(
        "myIdent = 3;",
        AST.ExpressionStatement(
            AST.BinaryExpression(
                AST.IdentifierExpression("myIdent".toSymbol().wrapMockValid()).wrapMockValid(),
                AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapMockValid()
        ).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseReturn() = expectNode(
        "return;",
        AST.ReturnStatement(null).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseReturnValue() = expectNode(
        "return(2);",
        AST.ReturnStatement(AST.LiteralExpression.Integer("2", false).wrapMockValid()).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicWhile() = expectNode(
        "while(2) {};",
        AST.WhileStatement(
            AST.LiteralExpression.Integer("2", false).wrapMockValid(),
            validEmptyBlock
        ).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIf() = expectNode(
        "if(2) {};",
        AST.IfStatement(
            AST.LiteralExpression.Integer("2", false).wrapMockValid(),
            validEmptyBlock,
            null
        ).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse() = expectNode(
        "if(2) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression.Integer("2", false).wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse_bool() = expectNode(
        "if(true) {} else {};",
        AST.IfStatement(
            AST.LiteralExpression.Boolean(true).wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
    ) { parseStatement(emptyAnchorSet) }

    @Test
    fun testParseBasicIfElse_ident() = expectNode(
        "if(myIdent) {} else {};",
        AST.IfStatement(
            AST.IdentifierExpression("myIdent".toSymbol().wrapMockValid()).wrapMockValid(),
            validEmptyBlock,
            validEmptyBlock
        ).wrapMockValid()
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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), AST.Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    AST.Type.Array(
                                        AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                    ).wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "i".toSymbol().wrapMockValid(),
                                        AST.Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        AST.Type.Integer.wrapMockValid(),
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
                                ),
                                Unit.wrapMockValid(), Unit.wrapMockValid()
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()
    ) { parse() }

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
                                "main".toSymbol().wrapMockValid(), AST.Type.Void.wrapMockValid(),
                                listOf(
                                    AST.Parameter(
                                        "args".toSymbol().wrapMockValid(),
                                        AST.Type.Array(
                                            AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    ).wrapMockValid()
                                ),
                                AST.Block(
                                    listOf(
                                        AST.IfStatement(
                                            AST.FieldAccessExpression(
                                                AST.LiteralExpression.Null().wrapMockValid(),
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
                                                        AST.LiteralExpression.Integer(
                                                            "472183921789789798798798798798787789738120391203213213",
                                                            false
                                                        )
                                                            .wrapMockValid()
                                                    ).wrapMockValid(),
                                                    AST.ReturnStatement(null).wrapMockValid(),
                                                    null
                                                ).wrapMockValid(),
                                                null
                                            ).wrapMockValid(),
                                            null
                                        ).wrapMockValid()
                                    ),
                                    Unit.wrapMockValid(), Unit.wrapMockValid()
                                ).wrapMockValid()
                            ).wrapMockValid()
                        )
                    ).wrapMockValid()
                )
            ).wrapMockValid()

        ) { parse() }
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
                            "m".toSymbol().wrapMockValid(), AST.Type.Void.wrapMockValid(),
                            listOf(),
                            AST.Block(
                                listOf(
                                    AST.ExpressionStatement(
                                        AST.ArrayAccessExpression(
                                            AST.ArrayAccessExpression(
                                                AST.IdentifierExpression("a".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.BinaryExpression(
                                                    AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                                                    AST.BinaryExpression(
                                                        AST.UnaryExpression(
                                                            AST.IdentifierExpression("i".toSymbol().wrapMockValid())
                                                                .wrapMockValid(),
                                                            AST.UnaryExpression.Operation.MINUS
                                                        ).wrapMockValid(),
                                                        AST.LiteralExpression.Integer("1", false).wrapMockValid(),
                                                        AST.BinaryExpression.Operation.ADDITION
                                                    ).wrapMockValid(),
                                                    AST.BinaryExpression.Operation.MULTIPLICATION
                                                ).wrapMockValid()
                                            ).wrapMockValid(),
                                            AST.LiteralExpression.Integer("2", false).wrapMockValid()
                                        ).wrapMockValid()
                                    ).wrapMockValid()
                                ),
                                Unit.wrapMockValid(), Unit.wrapMockValid()
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()
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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), AST.Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    AST.Type.Array(
                                        AST.Type.Array(
                                            AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                        ).wrapMockValid()
                                    )
                                        .wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        AST.Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression.Boolean(true).wrapMockValid(),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapMockValid()
                                        ).wrapMockValid(),
                                        null
                                    ).wrapMockValid()
                                ),
                                Unit.wrapMockValid(), Unit.wrapMockValid()
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()

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
                    "Main".toSymbol().wrapMockValid(),
                    listOf(
                        AST.MainMethod(
                            "main".toSymbol().wrapMockValid(), AST.Type.Void.wrapMockValid(),
                            listOf(
                                AST.Parameter(
                                    "args".toSymbol().wrapMockValid(),
                                    AST.Type.Array(
                                        AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()
                                    ).wrapMockValid()
                                ).wrapMockValid()
                            ),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement(
                                        "x".toSymbol().wrapMockValid(),
                                        AST.Type.Integer.wrapMockValid(),
                                        null
                                    )
                                        .wrapMockValid(),
                                    AST.IfStatement(
                                        AST.LiteralExpression.Boolean(true).wrapMockValid(),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x".toSymbol().wrapMockValid())
                                                    .wrapMockValid(),
                                                AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            ).wrapMockValid()
                                        ).wrapMockValid(),
                                        null
                                    ).wrapMockValid()
                                ),
                                Unit.wrapMockValid(), Unit.wrapMockValid()
                            ).wrapMockValid()
                        ).wrapMockValid()
                    )
                ).wrapMockValid()
            )
        ).wrapMockValid()

    ) { parse() }

    @Test
    fun testBasicBlock() = expectAst(
        "class test { public void test() { } }",
        astOf {
            clazz("test") {
                method("test", AST.Type.Void) {
                }
            }
        }
    )

    @Test
    fun testBlockWithEmptyStatements() = expectAst(
        "class test { public void test() { ;; } }",
        astOf {
            clazz("test") {
                method("test", AST.Type.Void) {
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
                    field("myIdent", AST.Type.Boolean)
                    field("myIdent2", AST.Type.Void)
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
                    field("myArray", AST.Type.Array(AST.Type.Array(AST.Type.Boolean.wrapMockValid()).wrapMockValid()))
                    field("myArray2", AST.Type.Array(AST.Type.Void.wrapMockValid()))
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
                    method("nomain", AST.Type.Void) {}
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
                        "nomain", AST.Type.Void,
                        param("ident", AST.Type.Boolean),
                        param("ident2", AST.Type.Class("myClass".toSymbol().wrapMockValid()))
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
                        "mymain", AST.Type.Void,
                        AST.Parameter(
                            "arr".toSymbol().wrapMockValid(),
                            AST.Type.Array(
                                AST.Type.Array(AST.Type.Class("Strig".toSymbol().wrapMockValid()).wrapMockValid()).wrapMockValid()
                            ).wrapMockValid()
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
                        AST.Type.Void,
                        AST.Parameter(
                            "args".toSymbol().wrapMockValid(),
                            AST.Type.Array(AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()).wrapMockValid()
                        )
                    ) {
                        localDeclaration(
                            "abc",
                            AST.Type.Array(AST.Type.Array(AST.Type.Integer.wrapMockValid()).wrapMockValid())
                        ) {
                            newArrayOf(
                                AST.Type.Array(
                                    AST.Type.Array(AST.Type.Integer.wrapMockValid()).wrapMockValid()
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
                        AST.Type.Void,
                        AST.Parameter(
                            "args".toSymbol().wrapMockValid(),
                            AST.Type.Array(AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()).wrapMockValid()
                        )
                    ) {
                        localDeclaration(
                            "abc",
                            AST.Type.Array(
                                AST.Type.Array(
                                    AST.Type.Array(AST.Type.Class("SomeClass".toSymbol().wrapMockValid()).wrapMockValid()).wrapMockValid()
                                ).wrapMockValid()
                            )
                        ) {
                            newArrayOf(
                                AST.Type.Array(
                                    AST.Type.Array(
                                        AST.Type.Array(
                                            AST.Type.Class("SomeClass".toSymbol().wrapMockValid()).wrapMockValid()
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
                        "main", AST.Type.Void,
                        AST.Parameter(
                            "args".toSymbol().wrapMockValid(),
                            AST.Type.Array(AST.Type.Class("String".toSymbol().wrapMockValid()).wrapMockValid()).wrapMockValid()
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
