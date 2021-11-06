package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.astOf
import edu.kit.compiler.utils.createLexer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class MixedParseTest {

    private fun expectAst(input: String, expectedAST: List<AST.ClassDeclaration>) =
        expectNode(input, expectedAST) { parseClassDeclarations() }

    private fun <T> expectNode(input: String, expectedNode: T, runParser: suspend Parser.() -> T) {
        val lexer = createLexer(input)
        val res = runBlocking {
            Parser(lexer.tokens()).also { it.initialize() }.runParser()
        }
        assertEquals(expectedNode, res)
    }

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
    fun testParseLiteral() = expectNode("1", AST.LiteralExpression(1)) { parseExpression() }

    @Test
    fun testParseIdentInExpr() = expectNode("myident", AST.IdentifierExpression("myident")) { parseExpression() }

    @Test
    fun testParseLocalInvocation() =
        expectNode("myident()", AST.MethodInvocationExpression(null, "myident", listOf())) { parseExpression() }

    @Test
    fun testParseLocalInvocationArg() = expectNode(
        "myident(1)",
        AST.MethodInvocationExpression(null, "myident", listOf(AST.LiteralExpression(1)))
    ) { parseExpression() }

    @Test
    fun testParseLocalInvocation3Arg() = expectNode(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident",
            listOf(AST.LiteralExpression(1), AST.LiteralExpression(2), AST.LiteralExpression(2))
        )
    ) { parseExpression() }

    @Test
    fun testParseAssignment() = expectNode(
        "myIdent = 3;",
        AST.ExpressionStatement(
            AST.BinaryExpression(
                AST.IdentifierExpression("myIdent"),
                AST.LiteralExpression(3),
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
        AST.ReturnStatement(AST.LiteralExpression(2))
    ) { parseStatement() }

    @Test
    fun testParseBasicWhile() = expectNode(
        "while(2) {};",
        AST.WhileStatement(AST.LiteralExpression(2), AST.Block(listOf()))
    ) { parseStatement() }

    @Test
    fun testParseBasicIf() = expectNode(
        "if(2) {};",
        AST.IfStatement(AST.LiteralExpression(2), AST.Block(listOf()), null)
    ) { parseStatement() }

    @Test
    fun testParseBasicIfElse() = expectNode(
        "if(2) {} else {};",
        AST.IfStatement(AST.LiteralExpression(2), AST.Block(listOf()), AST.Block(listOf()))
    ) { parseStatement() }



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
