package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.doNameAnalysis
import edu.kit.compiler.utils.assertAnnotations
import edu.kit.compiler.utils.createSemanticAST
import kotlin.test.Test
import kotlin.test.assertFalse

internal class UnusedDeclarationVisitorTest {

    private fun check(annotations: List<String>, input: () -> String) {
        val (ast, sourceFile, stringTable) = createSemanticAST(input())

        doNameAnalysis(ast, sourceFile, stringTable)
        sourceFile.printAnnotations()
        assertFalse(sourceFile.hasError)

        ast.accept(UnusedDeclarationVisitor(sourceFile))

        assertAnnotations(annotations, sourceFile)
    }

    @Test
    fun testUnused() {
        check(
            listOf(
                // "class `Main` is never used" -> not unused, because main class
                // "method `Main.main(...)` is never used" -> not unused, because main method
                "class `UnusedClass` is never used",
                "variable `u3` is never used",
                "parameter `u4` is never used",
                "method `Used.unused(...)` is never used",
                "field `Used.u5` is never used",
                // "method `UnusedClass.test()` is never used" -> unused, but suppressed because containing class is also unused
            )
        ) {
            """
                class Main {
                    public static void main(String[] args) {
                        Used u1 = new Used();
                        u1.test();
                        Used u2 = null;
                        if (u1 != null) {
                            u2.test();
                        }
                    }
                }

                class Used2 {}

                class UnusedClass {
                    public void test() {
                        Used2 u3 = null;
                    }
                }

                class Used {
                    public void test(Used2 u4) {}
                    public void unused() {}
                    public Used u5;
                }

            """.trimIndent()
        }
    }
}
