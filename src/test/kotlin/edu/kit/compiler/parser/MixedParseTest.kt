package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.utils.createLexer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MixedParseTest {

    @ExperimentalStdlibApi
    private fun expectAst(input: String, expectedAST: List<AST.ClassDeclaration>) {
        val lexer = createLexer(input)
        val res = runBlocking {
            Parser(lexer.tokens()).also { it.initialize() }.parseClassDeclarations()
        }
        assertEquals(expectedAST, res)
    }

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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
}
