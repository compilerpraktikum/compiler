package edu.kit.compiler.parser

import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.utils.TestUtils.emptyAnchorSet
import edu.kit.compiler.utils.TestUtils.withParser
import edu.kit.compiler.utils.debug
import edu.kit.compiler.wrapper.wrappers.AbstractAstVisitor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

data class RangeLine(val ranges: MutableMap<Int, MutableList<SourceRange>>, val currentLine: Int) {

    /**
     * Insert a new range into the set of ranges. This will greedily use the first line, that has enough space available
     */
    fun append(newRange: SourceRange) {
        if (ranges.isEmpty()) {
            ranges[1] = mutableListOf(newRange)
        } else {
            val firstLineWithEnoughSpace = ranges.entries.firstOrNull {
                it.value.last().last.offset < newRange.start.offset
            }?.key
            if (firstLineWithEnoughSpace == null) {
                ranges[ranges.keys.maxOf { it } + 1] = mutableListOf(newRange)
            } else {
                ranges[firstLineWithEnoughSpace]!!.add(newRange)
            }
        }
    }

    private fun drawRange(offset: Int, start: Int, length: Int): String =
        " ".repeat(start - offset - 1) + when (length) {
            0 -> "-"
            1 -> "|"
            else -> "|" + "-".repeat(length - 2) + "|"
        }

    private fun drawRangeStart(offset: Int, start: Int): String =
        " ".repeat(start - offset - 1) + "|->"

    private fun drawRangeEnd(end: Int): String =
        " ".repeat(end - 3) + "<-|"

    fun format(): String =
        ranges.mapNotNull {
            var lastExtent = 0
            it.value.joinToString("") { range ->
                val res = if (range.start.line < currentLine && range.last.line == currentLine) {
                    drawRangeEnd(range.last.column)
                } else if (range.start.line == currentLine) {
                    if (range.last.line == currentLine) {
                        drawRange(lastExtent, range.start.column, range.length)
                    } else {
                        drawRangeStart(lastExtent, range.start.column)
                    }
                } else {
                    // range does neither start nor end in the current line
                    ""
                }
                lastExtent = range.last.column
                res
            }.ifEmpty { null }
        }.joinToString("\n") { "#${it.substring(1)}" }
}

class SourceRangeTest {
    class SourceRangeCollector(val ranges: MutableList<SourceRange> = mutableListOf()) : AbstractAstVisitor() {
        override fun visitSourceRange(sourceRange: SourceRange): SourceRange {
            ranges.add(sourceRange)
            return super.visitSourceRange(sourceRange)
        }
    }

    fun <T> expectRanges(code: String, runParser: Parser.() -> T, runVisitor: SourceRangeCollector.(T) -> Unit) {

        val (expectedRanges, source) = code.lineSequence().partition {
            it.startsWith("#")
        }
        println("source: $source")
        val parsed = withParser(source.joinToString("\n")) { runParser() }
        val sourceRangeCollector = SourceRangeCollector()
        println("parsed:    $parsed")
        sourceRangeCollector.runVisitor(parsed)
        val actualRanges = sourceRangeCollector.ranges

        val lastLine = actualRanges.map { it.last.line }.maxOrNull()
        val rangesByLines = (0..(lastLine ?: 0)).associateWith { currentLine ->
            actualRanges.filter { it.start.line <= currentLine && it.last.line >= currentLine }
                .sortedBy { it.length }
                .sortedBy { it.start.column }
        }

        val joinedRangesByLine = rangesByLines.mapValues { entry ->
            val rangeAnnotations = RangeLine(mutableMapOf(), entry.key)

            entry.value.forEach {
                rangeAnnotations.append(it)
            }

            rangeAnnotations
        }
        println("joined $joinedRangesByLine")
        val actual = joinedRangesByLine.values.zip(listOf("") + source).joinToString("\n") {
            it.second + "\n" + it.first.format()
        }
        println("[[ expected: ]]")
        println(code)
        println("[[ actual: ]]")
        println(actual.substring(2))
        assertEquals(code, actual.substring(2))
    }

    fun expectExpressionRange(code: String) = expectRanges(
        code,
        {
            parseExpression(
                anc = emptyAnchorSet,
                isParenthesized = false
            ).also { println("parsed w/o sources: ${it.debug()}") }
        },
        { visitExpression(it) }
    )

    fun expectClassMemberRange(code: String) = expectRanges(code, {
        parseFieldMethodPrefix(anc = emptyAnchorSet)
    }, { visitClassMember(it) })

    fun expectStatementRange(code: String) = expectRanges(code, {
        parseStatement(anc = emptyAnchorSet)
    }, { visitStatement(it) })

    @Test
    fun testBinOp() =
        expectExpressionRange(
            """
              2+3
            # | |
            # |-|
            """.trimIndent()
        )

    @Test
    fun testPrecedenceBinOp() =
        expectExpressionRange(
            """
                  2+3*3
                # | | |
                # |---|
                #   |-|
            """.trimIndent()
        )

    @Test
    fun testArrayExpression() =
        expectExpressionRange(
            """
                  myArray[2]
                # |-----| |
                # |-----|
                # |--------|
            """.trimIndent()
        )

    @Test
    fun testNestedArrayExpression() =
        expectExpressionRange(
            """
                  myArray[other[a[2]]]
                # |-----| |---| | |
                # |-----| |---| |
                # |------------------|
                #         |---------|
                #               |--|
            """.trimIndent()
        )

    @Test
    fun testNewArrayExpression() =
        expectExpressionRange(
            """
                  new int[2][]
                # |----------|
                #     |-| |
                #     |----|
                #     |------|
            """.trimIndent()
        )

    @Test
    fun testAssignmentExpression() =
        expectExpressionRange(
            """
                  arr[3] = 1    +2
                # |-| |    |     |
                # |-|      |-----|
                # |----|
                # |--------------|
            """.trimIndent()
        )

    @Test
    fun testBasicField() =
        expectClassMemberRange(
            """
                  int a;
                # |-| |
                # |----|
            """.trimIndent()
        )

    @Test
    fun testBasicMethod() =
        expectClassMemberRange(
            """
                  int testMethod() {}
                # |-| |--------|   ||
                # |-----------------|
            """.trimIndent()
        )

    @Test
    fun testReturnExpressionStatement() =
        expectStatementRange(
            """
                return 2;
            #   |-------|
            #          |
            """.trimIndent()
        )

    @Test
    fun testReturnStatement() =
        expectStatementRange(
            """
                return ;
            #   |------|
            """.trimIndent()
        )

    @Test
    fun testMethodWithBody() =
        expectClassMemberRange(
            """
                int testMethod() {
            #   |-| |--------|   |->
            #   |->
                    if(true) {
            #       |->
            #          |--|  |->
                        return 3;
            #           |-------|
            #                  |
                    } else {
            #              |->
            #     <-|
                        return 2;
            #           |-------|
            #                  |
                    }
            #     <-|
            #     <-|
                }
            # <-|
            # <-|
            """.trimIndent()
        )
}
