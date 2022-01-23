package edu.kit.compiler.optimization

import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.transform.Transformation
import edu.kit.compiler.utils.createSemanticAST
import firm.Firm
import firm.Program
import firm.Util
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.fail

@EnabledOnOs(OS.LINUX) // current version of jFirm / libfirm works only on linux
internal class OptimizationTests {

    @AfterEach
    fun cleanUp() {
        Firm.finish()
    }

    private fun check(input: String, optimization: Optimization) {
        val (program, sourceFile, stringTable) = createSemanticAST(input)
        doSemanticAnalysis(program, sourceFile, stringTable)
        if (sourceFile.hasError) {
            fail("invalid program")
        }

        Transformation.transform(program, stringTable)
        Util.lowerSels()

        Program.getGraphs().forEach {
            optimization.apply(it)
        }
    }

    @Test
    fun testRegressionShortCircuitWithAssignment() {
        check(
            """
            class a {
                public static void main(String[] args) {
                    boolean b = false;
                    boolean c;
                    b = false || (c = true);
                    new a().print(c);
                }

                public void print(boolean b) {
                    if (b) System.out.println(4); else System.out.println(5);
                }
            }
            """.trimIndent(),
            ConstantPropagationAndFolding
        )
    }

    @Test
    fun testRegressionWhileLoopWithUninitializedVariable() {
        check(
            """
                class A {
                    public static void main(String[] args) {
                        boolean b;
                        while (b) {}
                    }
                }
            """.trimIndent(),
            ConstantPropagationAndFolding
        )
    }
}
