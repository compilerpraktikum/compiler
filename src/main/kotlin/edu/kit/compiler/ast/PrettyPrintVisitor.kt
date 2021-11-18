package edu.kit.compiler.ast

import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.wrapper.IdentityClassDeclaration
import edu.kit.compiler.wrapper.IdentityProgram
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.into
import java.io.PrintStream
import java.util.Stack

class PrettyPrintVisitor(
    val printStream: PrintStream
) : ASTVisitor<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>> {

    private var currentIndentation: Int = 0
    private var startsNewLine: Boolean = false

    //    private var printParanthesesInCurrentExpression = true
    private var printParanthesesStack: Stack<Boolean> =
        Stack() // Usage. In a visit method: peek() is false, we always don't. if peek() is true, the current method decides, if it needs to be printed!

    override fun visit(program: IdentityProgram) {
        printParanthesesStack.push(true) // default: print parantheses. TODO put this in the constructor!!!

        program.classes
            .map { classDeclaration -> classDeclaration.into().v }
            .sortedBy { it.name }
            .forEach { classDeclaration -> classDeclaration.accept(this) }
    }

    override fun visit(classDeclaration: IdentityClassDeclaration) {
        println("class ${classDeclaration.name.text} {")
        doIndented {
            classDeclaration.member
                .map { classMember -> classMember.into().v }
                .sortedBy { classMember -> classMember.memberName }
                .sortedByDescending { it is AST.Method || it is AST.MainMethod }
                .forEach { classMember -> classMember.accept(this) }
        }
        println("}")
    }

    override fun visit(field: AST.Field<Identity<Of>>) {
        print("public ", true)
        field.type.into().v.into().accept(this)
        println(" ${field.name.text};")
    }

    override fun visit(mainMethod: AST.MainMethod<Identity<Of>, Identity<Of>, Identity<Of>>) {
        print("public static ", true)
        mainMethod.returnType.into().v.into().accept(this)
        print(" ${mainMethod.name.text}(")

        mainMethod.parameters.forEachIndexed { i, parameter ->
            if (i > 0) print(", ")
            parameter.into().v.into().accept(this)
        }
//        var i = 0
//        for (parameter in mainMethod.parameters) {
//            parameter.accept(this)
//            if (i < mainMethod.parameters.size - 1) {
//                print(", ")
//            }
//            i++
//        }
        print(")")
        if (mainMethod.throwsException != null) {
            print(" throws " + mainMethod.throwsException.text)
        }
        mainMethod.block.into().v.accept(this)
        println("")
    }

    override fun visit(method: AST.Method<Identity<Of>, Identity<Of>, Identity<Of>>) {
        print("public ", true)
        method.returnType.into().v.into().accept(this)
        print(" ${method.name.text}(")

        method.parameters.forEachIndexed { i, parameter ->
            if (i > 0) print(", ")
            parameter.into().v.into().accept(this)
        }
        print(")")
        if (method.throwsException != null) {
            print(" throws " + method.throwsException.text)
        }

        method.block.into().v.accept(this)
        println("")
    }

    override fun visit(parameter: AST.Parameter<Identity<Of>>) {
        // just for indentation
        parameter.type.into().v.into().accept(this)
        print(" ${parameter.name.text}")
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>) {
        print("", startsNewLine)
        startsNewLine = false
        localVariableDeclarationStatement.type.into().v.into().accept(this)
        print(" ${localVariableDeclarationStatement.name.text}", false)
        if (localVariableDeclarationStatement.initializer != null) {
            print(" = ")
            doParenthesizedMaybe(false) { localVariableDeclarationStatement.initializer.accept(this) }
        }
        print(";")
    }

    /**
     * removes nested empty blocks and reduces them into one. Example `{{{}}}` -> `{}`
     */
    private fun cleanupBlock(block: AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>): AST.Block<Identity<Of>, Identity<Of>, Identity<Of>> {
        val statements: List<AST.BlockStatement<Identity<Of>, Identity<Of>, Identity<Of>>> = block.statements
            .map { it.into().v.into() }
            .map { blockStatement ->
                when (blockStatement) {
                    is AST.LocalVariableDeclarationStatement -> blockStatement
                    is AST.StmtWrapper ->
                        when (blockStatement.statement) {
                            is AST.Block -> cleanupBlock(blockStatement.statement).wrapBlockStatement()
                            else -> blockStatement
                        }
                }
            }.filter { blockStatement ->
                !(
                    blockStatement is AST.StmtWrapper<*, *, *> &&
                        blockStatement.statement is AST.Block<*, *, *> &&
                        blockStatement.statement.statements.isEmpty()
                    )
            }
        return AST.Block(statements.map { Identity(it) })
    }

    override fun visit(block: AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>) {
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
                        .map { it.into().v.into() }
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

    override fun visit(ifStatement: AST.IfStatement<Identity<Of>, Identity<Of>, Identity<Of>>) {
        print("if (", startsNewLine)
        doParenthesizedMaybe(false) { ifStatement.condition.accept(this) }
        print(")")

        val hasElse = ifStatement.falseStatement != null
        val trueStatement = ifStatement.trueStatement.into().v.into()
        val falseStatement = ifStatement.falseStatement?.into()?.v?.into()
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

    // Begin Body -> True 1. Option { : " { }" , : " {\n ...} 2. Option Statement das nicht Block ist -> "\n", "..." einrücken
    override fun visit(whileStatement: AST.WhileStatement<Identity<Of>, Identity<Of>, Identity<Of>>) {
        print("while (", startsNewLine)
        doParenthesizedMaybe(false) { whileStatement.condition.accept(this) }
        print(")")

        val statement = whileStatement.statement.into().v.into()
        if (statement !is AST.Block) {
            startsNewLine = true
            println("")
            doIndented { doParenthesizedMaybe(true) { statement.accept(this) } }
        } else {
            doParenthesizedMaybe(true) { statement.accept(this) }
        }
        print("")
    }

    override fun visit(returnStatement: AST.ReturnStatement<Identity<Of>, Identity<Of>>) {
        print("return", startsNewLine)
        if (returnStatement.expression != null) {
            print(" ")
            doParenthesizedMaybe(false) { returnStatement.expression.accept(this) }
        }
        print(";")
    }

    override fun visit(binaryExpression: AST.BinaryExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            doParenthesizedMaybe(true) { binaryExpression.left.accept(this) }
            print(" " + binaryExpression.operation.repr + " ")
            doParenthesizedMaybe(true) { binaryExpression.right.accept(this) }
        }
    }

    override fun visit(unaryExpression: AST.UnaryExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            unaryExpression.operation.accept(this)
            doParenthesizedMaybe(true) { unaryExpression.expression.accept(this) }
        }
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            if (methodInvocationExpression.target != null) {
                doParenthesizedMaybe(true) { methodInvocationExpression.target.accept(this) }
                print(".")
            }
            print("${methodInvocationExpression.method.text}(")

            methodInvocationExpression.arguments.forEachIndexed { i, arg ->
                if (i > 0) print(", ")
                doParenthesizedMaybe(false) { arg.accept(this) }
            }
//            var i = 0
//            for (arg in methodInvocationExpression.arguments) {
//                doParenthesizedMaybe(false) { arg.accept(this) }
//                if (i < methodInvocationExpression.arguments.size - 1) {
//                    print(", ")
//                }
//                i++
//            }
            print(")")
        }
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            doParenthesizedMaybe(true) { fieldAccessExpression.target.accept(this) }
            print(".")
            print(fieldAccessExpression.field.text)
        }
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            doParenthesizedMaybe(true) { arrayAccessExpression.target.accept(this) }
            print("[")
            doParenthesizedMaybe(false) { arrayAccessExpression.index.accept(this) }
            print("]")
        }
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        // never print parentheses
        print(identifierExpression.name.text)
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        // never print parantheses
        print(literalExpression.value.toString())
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        printParanthesesMaybe {
            print("new ")
            print(newObjectExpression.clazz.text)
            print("()")
        }
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<Identity<Of>, Identity<Of>>) {
        printParanthesesMaybe {
            print("new ")
            newArrayExpression.type.into().v.into().elementType.into().v.into().baseType.accept(this)
            print("[")
            doParenthesizedMaybe(true) { newArrayExpression.length.accept(this) }
            print("]")
            print("[]".repeat(arrayDepthCount(newArrayExpression.type.into().v.into(), 0)))
        }
    }

    fun arrayDepthCount(type: Type<Identity<Of>>, acc: Int): Int {
        return when (type) {
            is Type.Array -> arrayDepthCount(type.arrayType.elementType.into().v.into(), acc + 1)
            else -> acc
        }
    }

    fun arrayDepthCount(type: Type.Array.ArrayType<Identity<Of>>, acc: Int): Int {
        return arrayDepthCount(type.elementType.into().v.into(), acc)
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

    override fun visit(arrayType: Type.Array<Identity<Of>>) {
        arrayType.arrayType.elementType.into().v.into().accept(this)
        print("[]")
    }

    override fun visit(classType: Type.Class) {
        print(classType.name.text)
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        TODO("maybe needed..")
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<Identity<Of>, Identity<Of>>) {
        print("", startsNewLine)

        doParenthesizedMaybe(false) { expressionStatement.expression.accept(this) } // no parantheses for expressions directly under root

        print(";")
    }

    private fun <T> doIndented(function: () -> T) {
        increase_indent()
        function()
        decrease_indent()
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

    private fun increase_indent(depth: Int = 1) {
        this.currentIndentation += depth
    }

    private fun decrease_indent(depth: Int = 1) {
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

    override fun visit(arrayType: Type.Array.ArrayType<Identity<Of>>) {
        // Nothing to do. This is handled by the visitors containing them
    }

    override fun visit(operation: AST.UnaryExpression.Operation) {
        print(operation.repr)
    }
}
