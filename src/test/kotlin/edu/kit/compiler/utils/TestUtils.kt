package edu.kit.compiler.utils

import edu.kit.compiler.ast.PrettyPrintVisitor
import edu.kit.compiler.ast.accept
import edu.kit.compiler.lex.LexerMjTestSuite
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.wrapper.IdentityProgram
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.LenientBlock
import edu.kit.compiler.wrapper.LenientClassDeclaration
import edu.kit.compiler.wrapper.LenientExpression
import edu.kit.compiler.wrapper.LenientProgram
import edu.kit.compiler.wrapper.LenientStatement
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Parsed
import edu.kit.compiler.wrapper.ParsedBlock
import edu.kit.compiler.wrapper.ParsedClassDeclaration
import edu.kit.compiler.wrapper.ParsedExpression
import edu.kit.compiler.wrapper.ParsedProgram
import edu.kit.compiler.wrapper.ParsedStatement
import edu.kit.compiler.wrapper.PositionedProgram
import edu.kit.compiler.wrapper.fmapParsed
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.validate
import edu.kit.compiler.wrapper.wrappers.FunctorAnnotated
import edu.kit.compiler.wrapper.wrappers.FunctorIdentity
import edu.kit.compiler.wrapper.wrappers.FunctorLenient
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.NaturalTransformation
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.into
import edu.kit.compiler.wrapper.wrappers.map1
import org.junit.jupiter.api.Assertions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals

object TestUtils {

    class TestFileArgument(val name: String, val path: Path) {
        // This is used for naming in the junit output
        override fun toString(): String = name
    }

    /** this is used to run multiple instances of the test:
     * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
     *
     * @param subdirectory Name of the subdirectory in `test-cases`, from where this function should get the test files
     *
     * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
     *         shouldn't be verbose
     */
    fun getTestSuiteFilesFor(subdirectory: String): Stream<TestFileArgument> {
        // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        val testFolderAbsolutePath =
            File(LexerMjTestSuite::class.java.protectionDomain.codeSource.location.toURI()).getPath()
        val projectRootDirectory = Paths.get(testFolderAbsolutePath).parent.parent.parent.parent
        val path = projectRootDirectory.resolve("test-cases").resolve(subdirectory)
        return path.listDirectoryEntries("*.mj").map { TestFileArgument(path.relativize(it).name, it) }.stream()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun <T> expectNode(input: String, expectedNode: T, runParser: Parser.() -> T) {
        val (lexer, sourceFile) = createLexer(input)
        val res: T = Parser(sourceFile, lexer.tokens()).runParser()
        Assertions.assertEquals(expectedNode, res)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createAST(input: String): Parsed<ParsedProgram> {
        val (lexer, sourceFile) = createLexer(input)
        return Parser(sourceFile, lexer.tokens()).parse()
    }

    fun assertIdemPotence(input: String) {
        println("====[ input ]====")
        println(input)

        val ast1 = createAST(input)
        val pretty1 = prettyPrint(ast1.validate()!!)
        println("====[ pretty1 ]====")
        println(pretty1)

        val ast2 = createAST(pretty1)
        val pretty2 = prettyPrint(ast2.validate()!!)
        println("====[ pretty2 ]====")
        println(pretty2)

        assertEquals(pretty1, pretty2)
    }

    fun prettyPrint(astRoot: PositionedProgram): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val utf8: String = StandardCharsets.UTF_8.name()
        val printStream = PrintStream(byteArrayOutputStream, true, utf8)

        astRoot.accept(PrettyPrintVisitor(printStream))

        return byteArrayOutputStream.toString(utf8)
    }

    fun identityToPositioned(program: IdentityProgram, annotation: SourceRange): PositionedProgram {
        val transformToPositioned = object : NaturalTransformation<Identity<Of>, Positioned<Of>> {
            override fun <A> trans(fa: Kind<Identity<Of>, A>) = Positioned(fa.into().v, annotation)
        }

        return program.map1(FunctorAnnotated(), transformToPositioned)
    }

    fun annotatedToIdentity(annProgram: PositionedProgram): IdentityProgram {
        val transformToIdentity = object : NaturalTransformation<Positioned<Of>, Identity<Of>> {
            override fun <A> trans(fa: Kind<Positioned<Of>, A>) = Identity(fa.into().value)
        }

        return annProgram.map1(FunctorIdentity, transformToIdentity)
    }

    object TransformParsedToLenient : NaturalTransformation<Parsed<Of>, Lenient<Of>> {
        override fun <A> trans(fa: Kind<Parsed<Of>, A>): Kind<Lenient<Of>, A> = fa.into().unCompose.into().value
    }

    fun Parsed<ParsedProgram>.parsedToLenient(): Lenient<LenientProgram> =
        this.fmapParsed {
            it.map1(
                FunctorLenient,
                TransformParsedToLenient
            )
        }.unCompose.into().value.into()

    @JvmName("parsedToLenientParsedBlock")
    fun Parsed<ParsedBlock>.parsedToLenient(): Lenient<LenientBlock> =
        this.fmapParsed {
            it.map1(
                FunctorLenient,
                FunctorLenient,
                FunctorLenient,
                TransformParsedToLenient,
                TransformParsedToLenient,
                TransformParsedToLenient
            )
        }.unCompose.into().value.into()

    @JvmName("parsedToLenientParsedStatement")
    fun Parsed<ParsedStatement>.parsedToLenient(): Lenient<LenientStatement> =
        this.fmapParsed {
            it.map1(
                FunctorLenient,
                FunctorLenient,
                FunctorLenient,
                TransformParsedToLenient,
                TransformParsedToLenient,
                TransformParsedToLenient
            )
        }.unCompose.into().value.into()

    @JvmName("parsedToLenientParsedClassDeclaration")
    fun Parsed<ParsedClassDeclaration>.parsedToLenient(): Lenient<LenientClassDeclaration> =
        this.fmapParsed {
            it.map1(
                FunctorLenient,
                FunctorLenient,
                FunctorLenient,
                FunctorLenient,
                TransformParsedToLenient,
                TransformParsedToLenient,
                TransformParsedToLenient,
                TransformParsedToLenient
            )
        }.unCompose.into().value.into()

    @JvmName("parsedToLenientParsedExpression")
    fun Parsed<ParsedExpression>.parsedToLenient(): Lenient<LenientExpression> =
        this.fmapParsed {
            it.map1(
                FunctorLenient,
                FunctorLenient,
                TransformParsedToLenient,
                TransformParsedToLenient
            )
        }.unCompose.into().value.into()
}
