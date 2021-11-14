package edu.kit.compiler.prettyprinter

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.AbstractASTVisitor
import edu.kit.compiler.ast.Identity
import edu.kit.compiler.ast.Of
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.accept
import edu.kit.compiler.ast.into
import java.io.PrintStream
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.Stack

class PrettyPrintVisitor(
    val printStream: PrintStream
) : AbstractASTVisitor<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>() {

    private var currentIndentation: Int = 0
    private var startsNewLine: Boolean = false

    //    private var printParanthesesInCurrentExpression = true
    private var printParanthesesStack: Stack<Boolean> =
        Stack() // Usage. In a visit method: peek() is false, we always don't. if peek() is true, the current method decides, if it needs to be printed!

    override fun visit(program: AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>) {
        printParanthesesStack.push(true) // default: print parentheses. TODO put this in the constructor!!!

        program.classes
            .map { classDeclaration -> classDeclaration.into().v }
            .sortedBy { classDeclaration -> classDeclaration.name }
            .forEach { classDeclaration ->
                classDeclaration.accept(this)
            }
    }

    override fun visit(classDeclaration: AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>>) {
        println("class ${classDeclaration.name} {")
        doIndented {
            classDeclaration.member
                .map { it -> it.into().v }
                .sortedBy { classMember -> classMember.memberName }
                .sortedByDescending { it is AST.Method<*, *> }
                .sortedByDescending { it is AST.MainMethod<*, *> }
                .also { kotlin.io.println("members: $it") }
                .forEach { it.accept(this) }
        }
        println("}")
    }

    override fun visit(field: AST.Field) {
        print("public ", true)
        field.type.accept(this)
        println(" " + field.name + ";")
    }

    private fun <T> seperatedBy(seperator: String, input: List<T>, map: (T) -> Unit) {
        input.forEachIndexed { index, value ->
            if (index > 0) {
                print(seperator)
            }
            map(value)
        }
    }

    override fun visit(mainMethod: AST.MainMethod<Identity<Of>, Identity<Of>>) {
        print("public static ", true)
        mainMethod.returnType.accept(this)
        print(" " + mainMethod.name + "(")

        seperatedBy(", ", mainMethod.parameters) { parameter ->
            visit(parameter)
        }

        print(")")
        if (mainMethod.throwException != null) {
            print(" throws " + mainMethod.throwException.name)
        }
        visit(mainMethod.block.into().v)
        println("")
    }

    override fun visit(method: AST.Method<Identity<Of>, Identity<Of>>) {
        print("public ", true)
        method.returnType.accept(this)
        print(" " + method.name + "(")

        seperatedBy(", ", method.parameters) { parameter ->
            visit(parameter)
        }

        print(")")
        if (method.throwException != null) {
            print(" throws " + method.throwException.name)
        }

        visit(method.block.into().v)
        println("")
    }

    override fun visit(parameter: AST.Parameter) {
        // just for indentation
        parameter.type.accept(this)
        print(" " + parameter.name)
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<Identity<Of>>) {
        print("", startsNewLine)
        startsNewLine = false
        localVariableDeclarationStatement.type.accept(this)
        print(" " + localVariableDeclarationStatement.name, false)
        if (localVariableDeclarationStatement.initializer != null) {
            print(" = ")
            doParenthesizedMaybe(false) { visit(localVariableDeclarationStatement.initializer.into().v.into()) }
        }
        println(";")
    }

    fun visitMethodBody(block: AST.Block<Identity<Of>, Identity<Of>>) {
        // this block prints no braces, but it in- and dedents!
        if (block.statements.isEmpty()) {
            print(" {", false)
            println(" }")
        } else {
            println(" {", false)
            doIndented { block.statements.forEach { blockStatement -> visitStatement(blockStatement.into().v.into()) } }
            println("}", true)
        }
    }

    override fun visit(block: AST.Block<Identity<Of>, Identity<Of>>) {

        if (!startsNewLine) {
            print(" ")
        }
        if (block.statements.isEmpty()) {
            if (startsNewLine)
                println("{ }", startsNewLine)
            else
                print("{ }", startsNewLine)
        } else {
            println("{", startsNewLine)
            startsNewLine = true
            doIndented { block.statements.forEach { blockStatement -> visitStatement(blockStatement.into().v.into()) } }
            print("}", true)
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

    fun visitStatement(statement: AST.Statement<Identity<Of>, Identity<Of>>) = when (statement) {
        is AST.Block -> visit(statement)
        is AST.EmptyStatement -> TODO()
        is AST.ExpressionStatement -> visit(statement)
        is AST.IfStatement -> visit(statement)
        is AST.ReturnStatement -> visit(statement)
        is AST.WhileStatement -> visit(statement)
    }

    override fun visit(ifStatement: AST.IfStatement<Identity<Of>, Identity<Of>>) {
        print("if (", startsNewLine)
        doParenthesizedMaybe(false) { ifStatement.condition.into().v.into().accept(this) }
        print(")")

        val hasElse = ifStatement.falseStatement != null
        val hasThenBrackets = ifStatement.trueStatement is AST.Block<*, *>
        val hasElseBrackets = ifStatement.falseStatement is AST.Block<*, *>
        val elseif = hasElse && ifStatement.falseStatement is AST.IfStatement<*, *>
        val trueStatement = ifStatement.trueStatement.into().v.into()
        val falseStatement = ifStatement.falseStatement?.into()?.v?.into()

        val thenNewLine = hasElse && !hasThenBrackets
        if (hasElse) {
            if (!hasThenBrackets) {
                println("")
                doIndented { doParenthesizedMaybe(true) { trueStatement.accept(this) } }
                if (hasElseBrackets) {
                    print("else")
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    println("")
                } else {
                    if (elseif) {
                        print("else ", startsNewLine)
                        doParenthesizedMaybe(true) { falseStatement?.accept(this) }
//                        println("")
                    } else {
                        println("else", startsNewLine)
                        doIndented { doParenthesizedMaybe(true) { trueStatement.accept(this) } }
                    }
                }
            } else {
                doParenthesizedMaybe(true) { trueStatement.accept(this) }
                print(" else")
                if (elseif) {
                    print(" ")
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    println("")
                } else if (hasElseBrackets) {
                    doParenthesizedMaybe(true) { falseStatement?.accept(this) }
                    println("")
                } else {
                    println("")
                    doIndented { doParenthesizedMaybe(true) { falseStatement?.accept(this) } }
                    println("")
                }
            }
        } else if (hasThenBrackets) {
            doParenthesizedMaybe(true) { trueStatement.accept(this) }
            println("")
        } else {
            println(" ")
            doParenthesizedMaybe(true) { trueStatement.accept(this) }
        }
    }

    // Begin Body -> True 1. Option { : " { }" , : " {\n ...} 2. Option Statement das nicht Block ist -> "\n", "..." einrücken
    override fun visit(whileStatement: AST.WhileStatement<Identity<Of>, Identity<Of>>) {
        print("while (", startsNewLine)
        doParenthesizedMaybe(false) { whileStatement.condition.into().v.into().accept(this) }
        print(")")

        if (whileStatement.statement !is AST.Block<*, *>) {
            startsNewLine = true
            println("")
        }
        doParenthesizedMaybe(true) { whileStatement.statement.into().v.into().accept(this) }
        println("")
    }

    override fun visit(returnStatement: AST.ReturnStatement<Identity<Of>>) {
        print("return", startsNewLine)
        if (returnStatement.expression != null) {
            print(" ")
            doParenthesizedMaybe(false) { returnStatement.expression.into().v.into().accept(this) }
        }
        println(";")
    }

    override fun visit(expression: AST.Expression<Identity<Of>>) {
        // TODO remove this method
        throw RuntimeException("This should not be called. TODO remove from AbstractVisitor")
        print("TODO implement")
    }

    override fun visit(binaryExpression: AST.BinaryExpression<Identity<Of>>) {
        printParanthesesMaybe {
            doParenthesizedMaybe(true) { binaryExpression.left.accept(this) }
            print(" " + binaryExpression.operation.repr + " ")
            doParenthesizedMaybe(true) { binaryExpression.right.accept(this) }
        }
    }

    override fun visit(unaryExpression: AST.UnaryExpression<Identity<Of>>) {
        printParanthesesMaybe {
            print(unaryExpression.operation.repr)
            doParenthesizedMaybe(true) { unaryExpression.expression.accept(this) }
        }
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<Identity<Of>>) {
        printParanthesesMaybe {
            if (methodInvocationExpression.target != null) {
                doParenthesizedMaybe(true) { methodInvocationExpression.target.accept(this) }
                print(".")
            }
            print(methodInvocationExpression.method + "(")

            var i = 0
            for (arg in methodInvocationExpression.arguments) {
                doParenthesizedMaybe(false) { arg.accept(this) }
                if (i < methodInvocationExpression.arguments.size - 1) {
                    print(", ")
                }
                i++
            }
            print(")")
        }
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<Identity<Of>>) {
        printParanthesesMaybe {
            if (fieldAccessExpression.target != null) {
                doParenthesizedMaybe(true) { fieldAccessExpression.target.accept(this) }
                print(".")
            }
            print(fieldAccessExpression.field)
        }
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<Identity<Of>>) {
        printParanthesesMaybe {
            arrayAccessExpression.target.accept(this)
            print("[")
            doParenthesizedMaybe(false) { arrayAccessExpression.index.accept(this) }
            print("]")
        }
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        // never print parantheses
        print(identifierExpression.name)
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        // never print parantheses
        print(literalExpression.value.toString())
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        printParanthesesMaybe {
            print("new ")
            print(newObjectExpression.clazz)
            print("()")
        }
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<Identity<Of>>) {
        printParanthesesMaybe {
            print("new ")
            newArrayExpression.type.accept(this)
            print("[")
            doParenthesizedMaybe(true) { newArrayExpression.length.accept(this) }
            print("]")
            print("[]".repeat(arrayDepthCount(newArrayExpression.type.elementType, 0)))
        }
    }

    fun arrayDepthCount(type: Type, acc: Int): Int {
        return when (type) {
            is Type.Array -> arrayDepthCount(type.elementType, acc + 1)
            else -> acc
        }
    }

    fun arrayDepthCount(type: Type.Array, acc: Int): Int {
        return arrayDepthCount(type.elementType, acc + 1)
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

    override fun visit(arrayType: Type.Array) {
        arrayType.elementType.accept(this)
        print("[]")
    }

    override fun visit(classType: Type.ClassType) {
        print(classType.identifier)
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        TODO("maybe needed..")
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<Identity<Of>>) {
        print("", startsNewLine)

        doParenthesizedMaybe(false) { expressionStatement.expression.accept(this) } // no parantheses for expressions directly under root

        println(";")
    }

    private fun <T> doIndented(function: () -> T) {
        increaseIndent()
        function()
        decreaseIndent()
    }

    private fun <T> printParanthesesMaybe(function: () -> T) {
        if (printParanthesesStack.peek()) print("(")
        function()
        if (printParanthesesStack.peek()) print(")")
    }

    private fun <T> doParenthesizedMaybe(printParentheses: Boolean, function: () -> T) {
        printParanthesesStack.push(printParentheses)
        function()
        printParanthesesStack.pop()
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

    private fun printNewLineInBody() {
        if (startsNewLine) print("\n", false)
        startsNewLine = false
    }

    override fun visit(emptyStatement: AST.EmptyStatement) {
        TODO("Not yet implemented")
    }
}
