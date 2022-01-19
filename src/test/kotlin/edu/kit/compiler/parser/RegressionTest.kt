package edu.kit.compiler.parser

import edu.kit.compiler.ast.validate
import edu.kit.compiler.utils.createParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test cases for correct parser behavior. The error recovery might be lost on those, but it is important that the
 * parser at least rejects them and doesn't crash (as it did in the past).
 */
class RegressionTest {

    @Test
    fun testIllegalMemberDefinitions() {
        testParse("class a { private int foo; int foo; foo; public int foo(); public int foo() {} public; }")
    }

    @Test
    fun testIllegalArgumentDefinition() {
        testParse("class a { public int foo() { foo.bar(x, return) } }")
    }

    @Test
    fun testHigherLookahead1() {
        testParse("class A { public static void main(String[] args) { int a = new int")
    }

    @Test
    fun testHigherLookahead2() {
        testParse("class A { public static void main(String[] args) { int a = new int[")
    }

    @Test
    fun testIncompleteMethodCall() {
        testParse("class A { public void test() { foo.bar(")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun testParse(input: String, shouldSucceed: Boolean = false) {
        val (parser, sourceFile) = createParser(input)
        val ast = parser.parse()

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, ast.validate() != null)
    }
}
