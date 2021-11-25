package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.baseType
import edu.kit.compiler.semantic.dimension
import java.io.PrintStream
import java.util.Stack

class PrettyPrintVisitor(val printStream: PrintStream) : AbstractVisitor() {
    private var currentIndentation: Int = 0
    private var startsNewLine: Boolean = false

    //    private var printParanthesesInCurrentExpression = true

    /**
     * Usage. In a visit method: peek() is false, we always don't. if peek() is true, the current method decides,
     * if it needs to be printed!
     */
    private var printParanthesesStack: Stack<Boolean> =
        Stack()

    override fun visitProgram(program: AstNode.Program) {
        printParanthesesStack.push(true) // default: print parantheses. TODO put this in the constructor!!!

        program.classes
            .sortedBy { it.name.symbol }
            .forEach { classDeclaration -> classDeclaration.accept(this) }
    }

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        println("class ${classDeclaration.name.symbol.text} {")
        doIndented {
            classDeclaration.members
                .sortedBy { classMember -> classMember.name.symbol }
                .sortedByDescending {
                    it is AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration ||
                        it is AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration
                }
                .forEach { classMember -> classMember.accept(this) }
        }
        println("}")
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        print("public ", true)
        fieldDeclaration.type.accept(this)
        println(" ${fieldDeclaration.name.symbol.text};")
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        print("public static ", true)
        printMethodWithoutModifiers(mainMethodDeclaration)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        print("public ", true)
        printMethodWithoutModifiers(methodDeclaration)
    }

    private fun printMethodWithoutModifiers(subroutineDeclaration: AstNode.ClassMember.SubroutineDeclaration) {
        subroutineDeclaration.returnType.accept(this)
        print(" ${subroutineDeclaration.name.symbol.text}(")

        subroutineDeclaration.parameters.forEachIndexed { i, parameter ->
            if (i > 0) print(", ")
            parameter.accept(this)
        }
        print(")")
        if (subroutineDeclaration.throwsException != null) {
            print(" throws " + subroutineDeclaration.throwsException.symbol.text)
        }

        subroutineDeclaration.block.accept(this)
        println()
    }

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        parameter.type.accept(this)
        print(" ${parameter.name.symbol.text}")
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        print("", startsNewLine)
        startsNewLine = false
        localVariableDeclaration.type.accept(this)
        print(" ${localVariableDeclaration.name.symbol.text}", false)
        if (localVariableDeclaration.initializer != null) {
            print(" = ")
            doParenthesizedMaybe(false) { localVariableDeclaration.initializer.accept(this) }
        }
        print(";")
    }

    /**
     * removes nested empty blocks and reduces them into one. Example `{{{}}}` -> `{}`
     */
    private fun cleanupBlock(block: AstNode.Statement.Block): AstNode.Statement.Block {
        val statements: List<AstNode.Statement> = block.statements
            .map { blockStatement ->
                when (blockStatement) {
                    is AstNode.Statement.Block -> cleanupBlock(blockStatement)
                    else -> blockStatement
                }
            }.filter { blockStatement ->
                !(blockStatement is AstNode.Statement.Block && blockStatement.statements.isEmpty())
            }
        return AstNode.Statement.Block(statements, block.sourceRange)
    }

    override fun visitBlock(block: AstNode.Statement.Block) {
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
                        .forEach { blockStatement ->
                            blockStatement.accept(this)
                            println("")
                        }
                }
                print("}", startsNewLine)
            }
        }
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        print("if (", startsNewLine)
        doParenthesizedMaybe(false) { ifStatement.condition.accept(this) }
        print(")")

        val hasElse = ifStatement.elseCase != null
        val trueStatement = ifStatement.thenCase
        val falseStatement = ifStatement.elseCase

        val hasThenBrackets = trueStatement is AstNode.Statement.Block
        val hasElseBrackets = falseStatement is AstNode.Statement.Block
        val elseif = hasElse && falseStatement is AstNode.Statement.IfStatement
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
                    println()
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
            println()
        }
    }

    // Begin Body -> True 1. Option { : " { }" , : " {\n ...} 2. Option Statement das nicht Block ist -> "\n", "..." einrücken
    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        print("while (", startsNewLine)
        doParenthesizedMaybe(false) { whileStatement.condition.accept(this) }
        print(")")

        val statement = whileStatement.statement
        if (statement !is AstNode.Statement.Block) {
            startsNewLine = true
            println("")
            doIndented { doParenthesizedMaybe(true) { statement.accept(this) } }
        } else {
            doParenthesizedMaybe(true) { statement.accept(this) }
        }
        print("")
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        print("return", startsNewLine)
        if (returnStatement.expression != null) {
            print(" ")
            doParenthesizedMaybe(false) { returnStatement.expression.accept(this) }
        }
        print(";")
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        printParenthesisMaybe {
            doParenthesizedMaybe(true) { binaryOperation.left.accept(this) }
            print(" " + binaryOperation.operation.repr + " ")
            doParenthesizedMaybe(true) { binaryOperation.right.accept(this) }
        }
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        printParenthesisMaybe {
            print(unaryOperation.operation.repr)
            doParenthesizedMaybe(true) { unaryOperation.inner.accept(this) }
        }
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        printParenthesisMaybe {
            if (methodInvocationExpression.target != null) {
                doParenthesizedMaybe(true) { methodInvocationExpression.target.accept(this) }
                print(".")
            }
            print("${methodInvocationExpression.method.symbol.text}(")

            methodInvocationExpression.arguments.forEachIndexed { i, arg ->
                if (i > 0) print(", ")
                doParenthesizedMaybe(false) { arg.accept(this) }
            }
            print(")")
        }
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        printParenthesisMaybe {
            doParenthesizedMaybe(true) { fieldAccessExpression.target.accept(this) }
            print(".")
            print(fieldAccessExpression.field.symbol.text)
        }
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        printParenthesisMaybe {
            doParenthesizedMaybe(true) { arrayAccessExpression.target.accept(this) }
            print("[")
            doParenthesizedMaybe(false) { arrayAccessExpression.index.accept(this) }
            print("]")
        }
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        print(identifierExpression.name.symbol.text)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        // TODO: -literal != -(literal), falls literal nur als negatives literal in den bounds
        print(literalIntExpression.value)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        print(literalBoolExpression.value.toString())
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        print("null")
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        printParenthesisMaybe {
            print("new ")
            print(newObjectExpression.clazz.symbol.text)
            print("()")
        }
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        printParenthesisMaybe {
            val element = newArrayExpression.type
            print("new ")
            element.baseType.accept(this)
            print("[")
            doParenthesizedMaybe(true) { newArrayExpression.length.accept(this) }
            print("]")
            // dimension - 1, because we already printed one dimension
            print("[]".repeat(element.dimension - 1))
        }
    }

    override fun visitVoidType() {
        print("void")
    }

    override fun visitIntType() {
        print("int")
    }

    override fun visitBoolType() {
        print("boolean")
    }

    override fun visitArrayType(arrayType: SemanticType.Array) {
        arrayType.elementType.accept(this)
        print("[]")
    }

    override fun visitComplexType(clazz: SemanticType.Class) {
        print(clazz.name.symbol.text)
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        print("", startsNewLine)
        doParenthesizedMaybe(false) { expressionStatement.expression.accept(this) }
        print(";")
    }

    private fun <T> doIndented(function: () -> T) {
        increaseIndent()
        function()
        decreaseIndent()
    }

    private fun <T> printParenthesisMaybe(function: () -> T) {
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

    private fun println() {
        println("")
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
}
