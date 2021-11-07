package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.astOf
import edu.kit.compiler.utils.TestUtils.expectNode
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class MixedParseTest {

    private fun expectAst(input: String, expectedAST: List<AST.ClassDeclaration>) =
        expectNode(input, expectedAST) { parseClassDeclarations() }

    @Test
    fun testParseEmptyBlock() = expectNode("{}", AST.Block(listOf())) { parseBlock() }

    @Test
    fun testParseBlockOfEmptyBlocks() =
        expectNode("{{{}}}", AST.Block(listOf(AST.Block(listOf(AST.Block(listOf())))))) { parseBlock() }

    @Test
    fun testParseBlockWithEmptyStatement() = expectNode("{;}", AST.Block(listOf(AST.EmptyStatement))) { parseBlock() }

    @Test
    fun testParseBlockWithMultipleEmptyStatement() = expectNode(
        "{;;;;}",
        AST.Block(listOf(AST.EmptyStatement, AST.EmptyStatement, AST.EmptyStatement, AST.EmptyStatement))
    ) { parseBlock() }

    @Test
    fun testDisambiguateVarDeclarationAndExpression() = expectNode(
        "{ myident; mytype myident2; }",
        AST.Block(
            listOf(
                AST.ExpressionStatement(AST.IdentifierExpression("myident")),
                AST.LocalVariableDeclarationStatement("myident2", Type.ClassType("mytype"), null)
            )
        )
    ) { parseBlock() }

    @Test
    fun testParseAssignment() = expectNode(
        "myIdent = 3;",
        AST.ExpressionStatement(
            AST.BinaryExpression(
                AST.IdentifierExpression("myIdent"),
                AST.LiteralExpression("3"),
                AST.BinaryExpression.Operation.ASSIGNMENT
            )
        )
    ) { parseStatement() }

    @Test
    fun testParseReturn() = expectNode(
        "return;",
        AST.ReturnStatement(null)
    ) { parseStatement() }

    @Test
    fun testParseReturnValue() = expectNode(
        "return(2);",
        AST.ReturnStatement(AST.LiteralExpression("2"))
    ) { parseStatement() }

    @Test
    fun testParseBasicWhile() = expectNode(
        "while(2) {};",
        AST.WhileStatement(AST.LiteralExpression("2"), AST.Block(listOf()))
    ) { parseStatement() }

    @Test
    fun testParseBasicIf() = expectNode(
        "if(2) {};",
        AST.IfStatement(AST.LiteralExpression("2"), AST.Block(listOf()), null)
    ) { parseStatement() }

    @Test
    fun testParseBasicIfElse() = expectNode(
        "if(2) {} else {};",
        AST.IfStatement(AST.LiteralExpression("2"), AST.Block(listOf()), AST.Block(listOf()))
    ) { parseStatement() }

    @Test
    fun testParseBasicIfElse_bool() = expectNode(
        "if(true) {} else {};",
        AST.IfStatement(AST.LiteralExpression(true), AST.Block(listOf()), AST.Block(listOf()))
    ) { parseStatement() }

    @Test
    fun testParseBasicIfElse_ident() = expectNode(
        "if(myIdent) {} else {};",
        AST.IfStatement(AST.IdentifierExpression("myIdent"), AST.Block(listOf()), AST.Block(listOf()))
    ) { parseStatement() }

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
                    "Main",
                    listOf(
                        AST.MainMethod(
                            "main", Type.Void,
                            listOf(AST.Parameter("args", Type.Array(Type.ClassType("String")))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement("i", Type.Integer, null),
                                    AST.LocalVariableDeclarationStatement(
                                        "x",
                                        Type.Integer,
                                        AST.BinaryExpression(
                                            AST.IdentifierExpression("i"),
                                            AST.UnaryExpression(
                                                AST.IdentifierExpression("i"),
                                                AST.UnaryExpression.Operation.MINUS
                                            ),
                                            AST.BinaryExpression.Operation.ADDITION
                                        )
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        )
    ) { parseAST() }

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
                    "_Klasse",
                    listOf(
                        AST.MainMethod(
                            "main", Type.Void,
                            listOf(AST.Parameter("args", Type.Array(Type.ClassType("String")))),
                            AST.Block(
                                listOf(
                                    AST.IfStatement(
                                        AST.FieldAccessExpression(AST.LiteralExpression("null"), "nothing"),
                                        AST.IfStatement(
                                            AST.MethodInvocationExpression(AST.LiteralExpression(true), "fun", emptyList()),
                                            AST.IfStatement(
                                                AST.ArrayAccessExpression(
                                                    AST.LiteralExpression(false),
                                                    AST.LiteralExpression("472183921789789798798798798798787789738120391203213213")
                                                ),
                                                AST.ReturnStatement(null),
                                                null
                                            ),
                                            null
                                        ),
                                        null
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

    ) { parseAST() }

    @Test
    fun debugParserMJTest_newArrayWithAccess2() = expectNode(
        """
        class Test {
            public void m() {
                new int[0][0];
            }
        }
        """,
        AST.Program(
            listOf(
                AST.ClassDeclaration(
                    "Main",
                    listOf(
                        AST.MainMethod(
                            "main", Type.Void,
                            listOf(AST.Parameter("args", Type.Array(Type.ClassType("String")))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement("x", Type.Integer, null),
                                    AST.IfStatement(
                                        AST.LiteralExpression(true),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x"),
                                                AST.LiteralExpression("3"),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            )
                                        ),
                                        null
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

    ) { parseAST() }

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
                    "Main",
                    listOf(
                        AST.MainMethod(
                            "main", Type.Void,
                            listOf(AST.Parameter("args", Type.Array(Type.ClassType("String")))),
                            AST.Block(
                                listOf(
                                    AST.LocalVariableDeclarationStatement("x", Type.Integer, null),
                                    AST.IfStatement(
                                        AST.LiteralExpression(true),
                                        AST.ExpressionStatement(
                                            AST.BinaryExpression(
                                                AST.IdentifierExpression("x"),
                                                AST.LiteralExpression("3"),
                                                AST.BinaryExpression.Operation.ASSIGNMENT
                                            )
                                        ),
                                        null
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

    ) { parseAST() }

    @Test
    fun testBasicBlock() = expectAst(
        "class test { public void test() { } }",
        astOf {
            clazz("test") {
                method("test", Type.Void, listOf()) {
                }
            }
        }
    )

    @Test
    fun testBlockWithEmptyStatements() = expectAst(
        "class test { public void test() { ;; } }",
        astOf {
            clazz("test") {
                method("test", Type.Void, listOf()) {
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
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        emptyList()
                    )
                )
            }
        )
    }

    @Test
    fun testOneClasTwoFields() {
        expectAst(
            "class testClass { public boolean myIdent; public void myIdent2; }",
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        buildList<AST.ClassMember> {
                            add(
                                AST.Field(
                                    "myIdent",
                                    Type.Boolean
                                )
                            )
                            add(
                                AST.Field(
                                    "myIdent2",
                                    Type.Void
                                )
                            )
                        }
                    )
                )
            }
        )
    }

    @Test
    fun testOneClassArrayField() {
        expectAst(
            "class testClass { public boolean [] [] myArray; public void [] myArray2; }",
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        buildList<AST.ClassMember> {
                            add(
                                AST.Field(
                                    "myArray",
                                    Type.Array(Type.Array(Type.Boolean))
                                )
                            )
                            add(
                                AST.Field(
                                    "myArray2",
                                    Type.Array(Type.Void)
                                )
                            )
                        }
                    )
                )
            }
        )
    }

    @Test
    fun testOneClassMethod() {
        expectAst(
            "class testClass { public void nomain() {} }",
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        buildList<AST.ClassMember> {
                            add(
                                AST.Method(
                                    "nomain",
                                    Type.Void,
                                    emptyList(),
                                    AST.Block(
                                        emptyList()
                                    )
                                )
                            )
                        }
                    )
                )
            }
        )
    }

    @Test
    fun testOneMethodWithParams() {
        expectAst(
            "class testClass { public void nomain(boolean ident, myClass ident2) {} }",
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        buildList<AST.ClassMember> {
                            add(
                                AST.Method(
                                    "nomain",
                                    Type.Void,
                                    buildList<AST.Parameter> {
                                        add(
                                            AST.Parameter(
                                                "ident",
                                                Type.Boolean
                                            )
                                        )
                                        add(
                                            AST.Parameter(
                                                "ident2",
                                                Type.ClassType(
                                                    "myClass"
                                                )
                                            )
                                        )
                                    },
                                    AST.Block(
                                        emptyList()
                                    )
                                )
                            )
                        }
                    )
                )
            }
        )
    }

    @Test
    fun testOneMethodOneMainMethod() {
        expectAst(
            "class testClass { public static void mymain(Strig[][] arr ) {} }",
            buildList<AST.ClassDeclaration> {
                add(
                    AST.ClassDeclaration(
                        "testClass",
                        buildList<AST.ClassMember> {
                            add(
                                AST.MainMethod(
                                    "mymain",
                                    Type.Void,
                                    buildList<AST.Parameter> {
                                        add(
                                            AST.Parameter(
                                                "arr",
                                                Type.Array(Type.Array(Type.ClassType("Strig")))
                                            )
                                        )
                                    },
                                    AST.Block(
                                        emptyList()
                                    )
                                )
                            )
                        }
                    )
                )
            }
        )
    }
}
