package edu.kit.compiler.ast

import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.PositionedClassDeclaration
import edu.kit.compiler.wrapper.PositionedProgram
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.UnwrappableAnnotated
import edu.kit.compiler.wrapper.wrappers.annotationValue
import edu.kit.compiler.wrapper.wrappers.into
import edu.kit.compiler.wrapper.wrappers.mapValue
import edu.kit.compiler.wrapper.wrappers.unwrapAnnotated
import java.io.PrintStream
import java.util.Stack

private val positionedUnwrapper = UnwrappableAnnotated<SourceRange>()

class PrettyPrintVisitor(
    private val printStream: PrintStream
) : AbstractASTVisitor<Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>(
    positionedUnwrapper, positionedUnwrapper, positionedUnwrapper, positionedUnwrapper, positionedUnwrapper
) {

    private var currentIndentation: Int = 0
    private var startsNewLine: Boolean = false

    //    private var printParenthesesInCurrentExpression = true
    private var printParenthesesStack: Stack<Boolean> =
        Stack() // Usage. In a visit method: peek() is false, we always don't. if peek() is true, the current method decides, if it needs to be printed!

    override fun visit(program: PositionedProgram) {
        printParenthesesStack.push(true) // default: print parentheses. TODO put this in the constructor!!!

        program.classes
            .map { classDeclaration -> classDeclaration.into().unwrapAnnotated() }
            .sortedBy { it.name.text }
            .forEach { classDeclaration -> classDeclaration.accept(this) }
    }

    override fun visit(classDeclaration: PositionedClassDeclaration) {
        println("class ${classDeclaration.name.text} {")
        doIndented {
            classDeclaration.member
                .map { classMember -> classMember.into().unwrapAnnotated() }
                .sortedBy { classMember -> classMember.memberName.text }
                .sortedByDescending { it is AST.Method || it is AST.MainMethod }
                .forEach { classMember -> classMember.accept(this) }
        }
        println("}")
    }

    override fun visit(field: AST.Field<Positioned<Of>>) {
        print("public ", true)
        field.descendType()
        println(" ${field.name.text};")
    }

    override fun visit(mainMethod: AST.MainMethod<Positioned<Of>, Positioned<Of>, Positioned<Of>>) {
        print("public static ", true)

        mainMethod.descendReturnType()

        print(" ${mainMethod.name.text}(")
        mainMethod.parameters.forEachIndexed { i, parameter ->
            if (i > 0) print(", ")
            parameter.into().unwrapAnnotated().into().accept(this)
        }
        print(")")

        if (mainMethod.throwsException != null) {
            print(" throws " + mainMethod.throwsException.text)
        }

        mainMethod.descendBlock()
        println("")
    }

    override fun visit(method: AST.Method<Positioned<Of>, Positioned<Of>, Positioned<Of>>) {
        print("public ", true)

        method.descendReturnType()

        print(" ${method.name.text}(")
        method.parameters.forEachIndexed { i, parameter ->
            if (i > 0) print(", ")
            parameter.into().unwrapAnnotated().into().accept(this)
        }
        print(")")

        if (method.throwsException != null) {
            print(" throws " + method.throwsException.text)
        }

        method.descendBlock()
        println("")
    }

    override fun visit(parameter: AST.Parameter<Positioned<Of>>) {
        // just for indentation
        parameter.descendType()
        print(" ${parameter.name.text}")
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<Positioned<Of>, Positioned<Of>>) {
        print("", startsNewLine)
        startsNewLine = false
        localVariableDeclarationStatement.descendType()
        print(" ${localVariableDeclarationStatement.name.text}", false)
        if (localVariableDeclarationStatement.initializer != null) {
            print(" = ")
            doParenthesizedMaybe(false) { localVariableDeclarationStatement.descendInitializer() }
        }
        print(";")
    }

    /**
     * removes nested empty blocks and reduces them into one. Example `{{{}}}` -> `{}`
     */
    private fun cleanupBlock(block: AST.Block<Positioned<Of>, Positioned<Of>, Positioned<Of>>): AST.Block<Positioned<Of>, Positioned<Of>, Positioned<Of>> {
        val statements = block.statements
            .map {
                it.mapValue {
                    when (val blockStatement = it.into()) {
                        is AST.LocalVariableDeclarationStatement -> blockStatement
                        is AST.StmtWrapper ->
                            when (blockStatement.statement) {
                                is AST.Block -> cleanupBlock(blockStatement.statement).wrapBlockStatement()
                                else -> blockStatement
                            }
                    }
                }
            }.filter {
                it.unwrapAnnotated().into().let { blockStatement ->
                    !(
                        blockStatement is AST.StmtWrapper<*, *, *> &&
                            blockStatement.statement is AST.Block<*, *, *> &&
                            blockStatement.statement.statements.isEmpty()
                        )
                }
            }

        return AST.Block(statements)
    }

    override fun visit(block: AST.Block<Positioned<Of>, Positioned<Of>, Positioned<Of>>) {
        val cleanBlock = cleanupBlock(block)

        if (!startsNewLine) {
            print(" ")
        }
        if (cleanBlock.statements.isEmpty()) {
            print("{ }", startsNewLine)
        } else {
            if (cleanBlock.statements.isEmpty()) {
                print("{ }", startsNewLine)
            } else {
                println("{", startsNewLine)

                doIndented {
                    cleanBlock.statements
                        .map { it.into().unwrapAnnotated().into() }
                        .forEach { blockStatement ->
                            blockStatement.accept(this)
                            println("")
                        }
                }
                print("}", startsNewLine)
            }
        }
        /*    if (startsNewLine) {
            startsNewLine = false
            println("{")
            block.statements.forEach { blockStatement -> blockStatement.accept(this) }
            decrease_indent()
            println("}", true)
            increase_indent()
        } else {
            startsNewLine = false
            println("{", true)
            increase_indent()
            block.statements.forEach { blockStatement -> blockStatement.accept(this) }
            decrease_indent()
            println("}", true)
        }*/
    }

    override fun visit(ifStatement: AST.IfStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>) {
        print("if (", startsNewLine)
        doParenthesizedMaybe(false) { ifStatement.descendCondition() }
        print(")")

        val hasElse = ifStatement.falseStatement != null
        val trueStatement = ifStatement.trueStatement.into().unwrapAnnotated().into()
        val falseStatement = ifStatement.falseStatement?.into()?.unwrapAnnotated()?.into()
        val hasThenBrackets = trueStatement is AST.Block
        val hasElseBrackets = falseStatement is AST.Block
        val elseif = hasElse && falseStatement is AST.IfStatement
        if (hasElse) {
            if (!hasThenBrackets) {
                println("")
                doIndented { doParenthesizedMaybe(true) { trueStatement.accept(this) } }
                println("")
                if (hasElseBrackets) {
                    print("else", startsNewLine)
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    print("")
                } else {
                    if (elseif) {
                        print("else ", startsNewLine)
                        doParenthesizedMaybe(true) { falseStatement?.accept(this) }
//                        println("")
                    } else {
                        println("else", startsNewLine)
                        doIndented { doParenthesizedMaybe(true) { falseStatement?.accept(this) } }
                    }
                }
            } else {
                doParenthesizedMaybe(true) { trueStatement.accept(this) }
                print(" else")
                if (elseif) {
                    print(" ")
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    print("")
                } else if (hasElseBrackets) {
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    print("")
                } else {
                    println("")
                    doIndented { doParenthesizedMaybe(true) { falseStatement?.accept(this) } }
                    print("")
                }
            }
        } else if (hasThenBrackets) {
            doParenthesizedMaybe(true) { trueStatement.accept(this) }
            println("")
        } else {
            print(" ")
            doParenthesizedMaybe(true) { trueStatement.accept(this) }
            println("")
        }
    }

    // Begin Body -> True 1. Option { : " { }" , : " {\n ...} 2. Option Statement which is not block -> "\n", "..." indent
    override fun visit(whileStatement: AST.WhileStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>) {
        print("while (", startsNewLine)
        doParenthesizedMaybe(false) { whileStatement.descendCondition() }
        print(")")

        val statement = whileStatement.statement.into().unwrapAnnotated().into()
        if (statement !is AST.Block) {
            startsNewLine = true
            println("")
            doIndented { doParenthesizedMaybe(true) { statement.accept(this) } }
        } else {
            doParenthesizedMaybe(true) { statement.accept(this) }
        }
        print("")
    }

    override fun visit(returnStatement: AST.ReturnStatement<Positioned<Of>, Positioned<Of>>) {
        print("return", startsNewLine)
        if (returnStatement.expression != null) {
            print(" ")
            doParenthesizedMaybe(false) { returnStatement.descendExpression() }
        }
        print(";")
    }

    override fun visit(binaryExpression: AST.BinaryExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            doParenthesizedMaybe(true) { binaryExpression.descendLeft() }
            print(" " + binaryExpression.operation.repr + " ")
            doParenthesizedMaybe(true) { binaryExpression.descendRight() }
        }
    }

    override fun visit(unaryExpression: AST.UnaryExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            unaryExpression.operation.accept(this)
            doParenthesizedMaybe(true) { unaryExpression.descendExpression() }
        }
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            if (methodInvocationExpression.target != null) {
                doParenthesizedMaybe(true) { methodInvocationExpression.descendTarget() }
                print(".")
            }

            print("${methodInvocationExpression.method.text}(")

            methodInvocationExpression.arguments.forEachIndexed { i, arg ->
                if (i > 0) print(", ")
                doParenthesizedMaybe(false) { arg.into().unwrapAnnotated().into().accept(this) }
            }

            print(")")
        }
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            doParenthesizedMaybe(true) { fieldAccessExpression.descendTarget() }
            print(".")
            print(fieldAccessExpression.field.text)
        }
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            doParenthesizedMaybe(true) { arrayAccessExpression.descendTarget() }
            print("[")
            doParenthesizedMaybe(false) { arrayAccessExpression.descendIndexExpression() }
            print("]")
        }
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        // never print parentheses
        print(identifierExpression.name.text)
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        // never print parentheses
        print(literalExpression.value.toString())
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        printParenthesesMaybe {
            print("new ")
            print(newObjectExpression.clazz.text)
            print("()")
        }
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<Positioned<Of>, Positioned<Of>>) {
        printParenthesesMaybe {
            val element = newArrayExpression.type.annotationValue.into().elementType.annotationValue.into()
            print("new ")
            element.baseType.accept(this)
            print("[")
            doParenthesizedMaybe(true) { newArrayExpression.descendLength() }
            print("]")
            print("[]".repeat(element.dimension))
        }
    }

    override fun visit(voidType: Type.Void) {
        print("void")
    }

    override fun visit(integerType: Type.Integer) {
        print("int")
    }

    override fun visit(booleanType: Type.Boolean) {
        print("boolean")
    }

    override fun visit(arrayType: Type.Array<Positioned<Of>>) {
        arrayType.descendType()
        print("[]")
    }

    override fun visit(classType: Type.Class) {
        print(classType.name.text)
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        TODO("maybe needed..")
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<Positioned<Of>, Positioned<Of>>) {
        print("", startsNewLine)

        doParenthesizedMaybe(false) { expressionStatement.descendExpression() } // no parentheses for expressions directly under root

        print(";")
    }

    private fun <T> doIndented(function: () -> T) {
        increaseIndent()
        function()
        decreaseIndent()
    }

    private fun <T> printParenthesesMaybe(function: () -> T) {
        if (printParenthesesStack.peek()) print("(")
        function()
        if (printParenthesesStack.peek()) print(")")
    }

    private fun <T> doParenthesizedMaybe(printParentheses: Boolean, function: () -> T) {
        printParenthesesStack.push(printParentheses)
        function()
        printParenthesesStack.pop()
    }

    private fun increaseIndent(depth: Int = 1) {
        this.currentIndentation += depth
    }

    private fun decreaseIndent(depth: Int = 1) {
        this.currentIndentation -= depth
        if (this.currentIndentation < 0) throw IllegalStateException("Indentation < 0: " + this.currentIndentation)
    }

    private fun print(s: String, indent: Boolean = false) {
        if (indent) printStream.print("\t".repeat(this.currentIndentation))
        printStream.print(s)
        startsNewLine = false
    }

    private fun println(s: String, indent: Boolean = false) {
        print(s, indent)
        printStream.println()
        startsNewLine = true
    }

    override fun visit(arrayType: Type.Array.ArrayType<Positioned<Of>>) {
        // Nothing to do. This is handled by the visitors containing them
    }

    override fun visit(operation: AST.UnaryExpression.Operation) {
        print(operation.repr)
    }
}
