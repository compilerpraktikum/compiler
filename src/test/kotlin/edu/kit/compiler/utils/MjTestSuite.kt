package edu.kit.compiler.utils

import edu.kit.compiler.source.SourceFile
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.charset.MalformedInputException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MjTestSuite(
    private val testCaseDirectory: String,
    private val phase: String = testCaseDirectory,
) {
    companion object {
        @JvmStatic
        private fun assertCompilationResult(testCase: TestCase, successful: Boolean) {
            if (testCase.shouldSucceed) {
                assertTrue("expected failure, but got success for ${testCase.name}") { successful }
            } else {
                assertFalse("expected success, but got failure for ${testCase.name}") { successful }
            }
        }
    }

    class TestCase(val name: String, val path: Path) {
        // This is used for naming in the junit output
        override fun toString(): String = name

        val shouldSucceed: Boolean
            get() = !name.endsWith(".invalid.mj")
    }

    private fun provideTests(): Stream<TestCase> {
        val projectRoot = Paths.get("")
        val path = projectRoot.resolve("test-cases").resolve(testCaseDirectory)
        return path.listDirectoryEntries()
            .stream()
            .filter { it.extension in listOf("mj", "java") }
            .map { TestCase(path.relativize(it).name, it) }
    }

    protected class TestContext(
        val testCase: TestCase,
        val source: SourceFile,
    ) {

        /**
         * If test execution has already failed (determined by [successful]), asserts that the test is supposed to fail
         * and aborts further execution of the test. If the test has not failed yet, nothing happens.
         */
        @OptIn(ExperimentalContracts::class)
        fun checkStep(successful: Boolean) {
            contract {
                returns() implies (successful)
            }

            if (!successful) {
                assertCompilationResult(testCase, false)
                throw AbortTestException() // abort test as it has already succeeded
            }
        }

        /**
         * Asserts that the test result (determined by [successful]) is the supposed outcome of the test.
         */
        @OptIn(ExperimentalContracts::class)
        fun checkResult(successful: Boolean) {
            contract {
                returns() implies (successful)
            }

            assertCompilationResult(testCase, successful)
            assertTrue { testCase.shouldSucceed == !source.hasError }
        }
    }

    private class AbortTestException : Exception()

    @ParameterizedTest
    @MethodSource("provideTests")
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun test(testCase: TestCase) {
        println("[test:$phase] ${testCase.path}")

        val source = try {
            SourceFile.from(testCase.path)
        } catch (e: MalformedInputException) {
            if (testCase.shouldSucceed) {
                fail("expected success, but input contained invalid ASCII")
            }
            return
        }

        val context = TestContext(testCase, source)
        try {
            context.execute()
        } catch (e: AbortTestException) {
            // nothing to do
        } finally {
            source.printAnnotations()
        }
    }

    protected abstract fun TestContext.execute()
}
