package edu.kit.compiler.lex

import org.junit.jupiter.api.Test
import kotlin.io.path.toPath
import kotlin.test.expect

internal class SourceFileTest {

    private fun skipAll(sourceFile: SourceFile) {
        var c = sourceFile.next()
        while (c != InputProvider.END_OF_FILE) {
            c = sourceFile.next()
        }
    }

    @Test
    fun testGetLine() {
        val path = javaClass.getResource("/TestFile.txt")!!.toURI().toPath()
        val sourceFile = SourceFile.from(path)
        skipAll(sourceFile)
        expect("1 test 1") { sourceFile.getLine(1) }
        expect("2 asdf 2") { sourceFile.getLine(2) }
        expect("3 jklo 3") { sourceFile.getLine(3) }
    }
}
