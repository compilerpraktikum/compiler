package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.toASTOperation
import edu.kit.compiler.lex.AbstractLexer
import kotlinx.coroutines.flow.Flow

/**
 * Asynchronous parser that consumes a lexer flow generated by [AbstractLexer.tokens] and generates an [ASTNode] from
 * it.
 *
 * @param lexer [AbstractLexer] implementation providing a flow of [edu.kit.compiler.Token]
 */
@ExperimentalStdlibApi
class Parser(tokens: Flow<Token>) : AbstractParser(tokens) {

    /**
     * Parse the lexer stream into an AST. Suspends when the lexer isn't fast enough.
     */
    override suspend fun parseAST(): AST.Program {
        val classDeclarations = parseClassDeclarations()
        return AST.Program(classDeclarations)
    }

    private suspend fun parsePrimaryExpression(): AST.Expression {
        println("    debug in parsePrimaryExpression: peek next=" + peek(0) + " " + peek(1) + " " + peek(2))
        return when (val next = peek()) {
            is Token.Literal -> {
                next()
                AST.LiteralExpression(next.value.toInt())
            }
            is Token.Operator -> {
                println("        debug in parsePrimaryExpression.TokenOperator: peek next=" + peek(0) + " " + peek(1) + " " + peek(2))
                if (next.type == Token.Operator.Type.LParen) {
                    next()
                    val innerExpr = parseExpression(1)
                    println("        debug in parsePrimaryExpression.TokenOperator: innerExpr=$innerExpr")
                    println("        debug in parsePrimaryExpression.TokenOperator: peek next=" + peek(0) + " " + peek(1) + " " + peek(2))
                    val tokenAfterParens = this.next()
                    if (tokenAfterParens is Token.Operator && tokenAfterParens.type == Token.Operator.Type.RParen) {
                        innerExpr
                    } else {
                        // TODO proper error handling
                        throw IllegalArgumentException("expected closing RPAREN")
                    }
                } else {
                    throw IllegalArgumentException("unexpected operator: $next")
                }
            }
            is Token.Identifier -> {
                val reference = expectIdentifier()

                val maybeLParent = peek(0)
                var arguments: List<AST.Expression> = listOf()
                if (maybeLParent is Token.Operator && maybeLParent.type == Token.Operator.Type.LParen) {
                    expectOperator(Token.Operator.Type.LParen)
                    arguments = parseArguments()
                    expectOperator(Token.Operator.Type.RParen)

                    AST.MethodInvocationExpression(null, reference.name, arguments) // todo ist das richtig?
                } else AST.IdentifierExpression(reference.name)
            }
            is Token.Keyword -> {
                next() // TODO put the nexts out of the when?
                when (next.type) {
                    Token.Keyword.Type.Null -> TODO()
                    Token.Keyword.Type.False -> AST.LiteralExpression(false)
                    Token.Keyword.Type.True -> AST.LiteralExpression(true)
                    Token.Keyword.Type.This -> TODO()
                    Token.Keyword.Type.New -> TODO()
                    else -> throw IllegalArgumentException("unexpected keyword ${next.type}")
                }
            }
            else -> throw IllegalArgumentException("unexpected token $next")
        }
    }

    suspend fun parseArguments(): List<AST.Expression> {
        val arguments = mutableListOf<AST.Expression>()
        var nextToken = peek()
        while (!(nextToken is Token.Operator && nextToken.type == Token.Operator.Type.RParen)) {
            if (arguments.isNotEmpty()) expectOperator(Token.Operator.Type.Comma)
            arguments += parseExpression()
            nextToken = peek()
        }
        return arguments
    }

    suspend fun parsePostfixExpression(): AST.Expression {
        println(" debug in parsePostfixExpression: peek next=" + peek(0) + " " + peek(1) + " " + peek(2))
        val primaryExpression = parsePrimaryExpression()

        return when (val firstPeekedToken = peek()) {
            is Token.Operator ->
                when (firstPeekedToken.type) {
                    Token.Operator.Type.Dot,
                    Token.Operator.Type.LeftBracket -> parsePostfixOp(primaryExpression)
                    else -> return primaryExpression
                }
            else -> return primaryExpression
        }
    }

    suspend fun parsePostfixOp(target: AST.Expression): AST.Expression {
        println("PARSE_POSTFIX_OP:" + target + "    peek:" + peek(0) + " " + peek(1) + " " + peek(2))
        return when (val firstPeekedToken = peek()) {
            is Token.Operator ->
                when (firstPeekedToken.type) {
                    Token.Operator.Type.Dot -> {
                        expectOperator(Token.Operator.Type.Dot)
                        val ident = expectIdentifier()
                        val maybeLParent = peek()
                        if (maybeLParent is Token.Operator && maybeLParent.type == Token.Operator.Type.LParen) {
                            // methodInvocation todo recurse
                            expectOperator(Token.Operator.Type.LParen)
                            val arguments = parseArguments()
                            expectOperator(Token.Operator.Type.RParen)
                            parsePostfixOp(AST.MethodInvocationExpression(target, ident.name, arguments))
                        } else {
                            // fieldAccess todo recurse
                            parsePostfixOp(AST.FieldAccessExpression(target, ident.name))
                        }
                        // k=3 because lazy. Maybe change this if needed later on
                    }
                    Token.Operator.Type.LeftBracket -> {
                        expectOperator(Token.Operator.Type.LeftBracket)
                        val index = parseExpression()
                        expectOperator(Token.Operator.Type.RightBracket)
                        parsePostfixOp(AST.ArrayAccessExpression(target, index))
                    }
                    else -> target
                }
            else -> target
        }
    }

    suspend fun parseUnaryExpression(): AST.Expression =
        // todo not exhausting first(follow) of parsePrimary!)
        // TODO parsePostifixExpression instead of parsePrimaryExpression !
        when (val peeked = peek()) {
            is Token.Operator ->
                when (peeked.type) {
                    Token.Operator.Type.Not -> {
                        expectOperator(Token.Operator.Type.Not)
                        AST.UnaryExpression(parseUnaryExpression(), AST.UnaryExpression.Operation.NOT)
                    }
                    Token.Operator.Type.Minus -> {
                        expectOperator(Token.Operator.Type.Minus)
                        AST.UnaryExpression(parseUnaryExpression(), AST.UnaryExpression.Operation.MINUS)
                    }
                    else -> parsePostfixExpression()
                }
            else -> parsePostfixExpression()
        }

    suspend fun parseExpression(minPrecedence: Int = 1): AST.Expression {
        var result = parseUnaryExpression()
        var currentToken = peek()

        while (
            currentToken is Token.Operator &&
            (currentToken.type.toASTOperation()?.precedence?.let { it >= minPrecedence } == true)
        ) {
            val op = currentToken.type.toASTOperation()!!

            next()

            val rhs = parseExpression(
                when (op.associativity) {
                    AST.BinaryExpression.Operation.Associativity.LEFT -> op.precedence + 1
                    else -> op.precedence
                }
            )
            result = AST.BinaryExpression(result, rhs, op)
            currentToken = peek()
        }
        return result
    }

    suspend fun parseClassDeclarations(): List<AST.ClassDeclaration> {
        println("Peekstart " + peek())
        return buildList<AST.ClassDeclaration> {
            while (peek(0) != Token.Eof) {
                expectKeyword(Token.Keyword.Type.Class)
                val ident = expect<Token.Identifier>()
                expectOperator(Token.Operator.Type.LeftBrace)
                val classMembers = parseClassMembers()
                println("debug   " + peek(0))
                expectOperator(Token.Operator.Type.RightBrace)

                add(AST.ClassDeclaration(ident.name, classMembers))
                // val classNode constructClassNode(ident, classMembers)
            }
            expect<Token.Eof>()
        }
    }

    suspend fun parseClassMembers(): List<AST.ClassMember> {
        return buildList<AST.ClassMember> {
            var peeked = peek(0)
            while (peeked is Token.Keyword && peeked.type == Token.Keyword.Type.Public) {
                add(parseClassMember())
                peeked = peek(0)
            }
        }
    }

    suspend fun parseClassMember(): AST.ClassMember {
        println("DEBUG parseClassMember: PEEK(0) " + peek(0))
        expectKeyword(Token.Keyword.Type.Public)
        val token = peek(0)

        return when (token) {
            is Token.Keyword -> {
                return when (token.type) {
                    Token.Keyword.Type.Static -> parseMainMethod()
                    Token.Keyword.Type.Int, Token.Keyword.Type.Boolean, Token.Keyword.Type.Void, ->
                        parseFieldMethodPrefix()
                    else -> enterPanicMode() // todo right?
                }
            }
            is Token.Identifier -> {
                parseFieldMethodPrefix()
            }
            else -> enterPanicMode() // todo right?
        }
    }

    suspend fun parseMainMethod(): AST.MainMethod {
        expectKeyword(Token.Keyword.Type.Static)
        expectKeyword(Token.Keyword.Type.Void)

        val ident = expectIdentifier()

        expectOperator(Token.Operator.Type.LParen)
        val parameter = parseParameter()
        expectOperator(Token.Operator.Type.RParen)

        val maybeThrowsToken = peek(0)
        if (maybeThrowsToken is Token.Keyword && maybeThrowsToken.type == Token.Keyword.Type.Throws) {
            parseMethodRest()
        }

        val block = parseBlock()
        return AST.MainMethod(
            ident.name,
            Type.Void,
            listOf(parameter),
            block
        )
    }

    suspend fun parseFieldMethodPrefix(): AST.ClassMember {
        val type = parseType()
        val ident = expectIdentifier()
        val fieldMethodRestToken = peek(0)
        return when (fieldMethodRestToken) {
            is Token.Operator -> {
                when (fieldMethodRestToken.type) {
                    Token.Operator.Type.Semicolon -> parseField(ident, type)
                    Token.Operator.Type.LParen -> parseMethod(ident, type)
                    else -> enterPanicMode() // todo right?
                }
            }
            else -> enterPanicMode() // todo right?
        }
    }

    suspend fun parseField(ident: Token.Identifier, type: Type): AST.Field {
        expectOperator(Token.Operator.Type.Semicolon)
        return AST.Field(
            ident.name,
            type
        )
    }
    suspend fun parseMethod(ident: Token.Identifier, type: Type): AST.Method {
        expectOperator(Token.Operator.Type.LParen)
        val maybeRParenToken = peek(0)
        val parameters = if (!(maybeRParenToken is Token.Operator && maybeRParenToken.type == Token.Operator.Type.RParen)) {
            parseParameters()
        } else emptyList()
        expectOperator(Token.Operator.Type.RParen)

        val maybeThrowsToken = peek(0)
        if (maybeThrowsToken is Token.Keyword && maybeThrowsToken.type == Token.Keyword.Type.Throws) {
            parseMethodRest()
        }
        val block = parseBlock()
        return AST.Method(
            ident.name,
            type,
            parameters,
            block
        )
    }

    suspend fun parseBlock(): AST.Block {
        expectOperator(Token.Operator.Type.LeftBrace)

        val maybeRightBrace = peek(0)
        val resultBlock = AST.Block(
            if (!(maybeRightBrace is Token.Operator && maybeRightBrace.type == Token.Operator.Type.RightBrace)) {
                parseBlockStatements()
            } else emptyList()
        )

        expectOperator(Token.Operator.Type.RightBrace)
        return resultBlock
    }

    suspend fun parseBlockStatements(): List<AST.BlockStatement> {
        // at least one should exist at this point.
        return buildList<AST.BlockStatement> {
            var peeked = peek(0)
            while (!(peeked is Token.Operator && peeked.type == Token.Operator.Type.RightBrace)) {
                add(parseBlockStatement())
                peeked = peek(0)
            }
        }
    }

    suspend fun parseBlockStatement(): AST.BlockStatement {
        // Statement ==> "{ | ; | if | while | return | null | false | true | INTEGER_LITERAL | ( | IDENT | this | new"
        // Statement: Auf IDENT folgt nie ein weiteres IDENT.
        // LocalVariableDeclarationStatement ==> "int | boolean | void | IDENT" x " IDENT " x " = | ; "
        println("in parseBlockStatement with peek(0)=" + peek(0))
        return when (val firstToken = peek(0)) {
            is Token.Keyword -> {
                when (firstToken.type) {
                    Token.Keyword.Type.If,
                    Token.Keyword.Type.While,
                    Token.Keyword.Type.Return,
                    Token.Keyword.Type.Null,
                    Token.Keyword.Type.False,
                    Token.Keyword.Type.True,
                    Token.Keyword.Type.This,
                    Token.Keyword.Type.New -> parseStatement()

                    Token.Keyword.Type.Int,
                    Token.Keyword.Type.Boolean,
                    Token.Keyword.Type.Void -> parseLocalVariableDeclarationStatement()

                    else -> enterPanicMode() // TODO this might not be the right place
                }
            }
            is Token.Literal -> parseStatement()
            is Token.Operator -> {
                when (firstToken.type) {
                    Token.Operator.Type.LeftBrace,
                    Token.Operator.Type.Semicolon,
                    Token.Operator.Type.LeftBrace,
                    Token.Operator.Type.Not,
                    Token.Operator.Type.Minus,
                    Token.Operator.Type.LParen -> parseStatement()

                    else -> enterPanicMode() // TODO this might not be the right place
                }
            }
            is Token.Identifier -> {
                // Lookahead = 2 needed, here!
                when (val secondToken = peek(1)) {
                    is Token.Identifier -> parseLocalVariableDeclarationStatement()
                    else -> parseStatement()
                }
            }
            else -> enterPanicMode()
        }
    }

    suspend fun parseStatement(): AST.Statement {
        println("  in parseStatement with peek(0)=" + peek(0))
        return when (val firstToken = peek(0)) {
            is Token.Operator -> {
                when (firstToken.type) {
                    Token.Operator.Type.LeftBrace -> parseBlock()
                    Token.Operator.Type.Semicolon -> parseEmptyStatement()
                    Token.Operator.Type.Not,
                    Token.Operator.Type.Minus,
                    Token.Operator.Type.LParen -> parseExpressionStatement()

                    else -> enterPanicMode() // TODO this might not be the right place
                }
            }
            is Token.Keyword -> {
                when (firstToken.type) {
                    Token.Keyword.Type.If -> parseIfStatement()
                    Token.Keyword.Type.While -> parseWhileStatement()
                    Token.Keyword.Type.Return -> parseReturnStatement()
                    Token.Keyword.Type.Null -> parseExpressionStatement()
                    Token.Keyword.Type.False -> parseExpressionStatement()
                    Token.Keyword.Type.True -> parseExpressionStatement()
                    Token.Keyword.Type.This -> parseExpressionStatement()
                    Token.Keyword.Type.New -> parseExpressionStatement()

                    else -> enterPanicMode() // TODO this might not be the right place
                }
            }
            is Token.Literal -> parseExpressionStatement()
            is Token.Identifier -> parseExpressionStatement()
            else -> enterPanicMode() // TODO this might not be the right place
        }
    }

    suspend fun parseReturnStatement(): AST.ReturnStatement {
        expectKeyword(Token.Keyword.Type.Return)
        val maybeSemicolon = peek(0)
        var returnValue: AST.Expression? = null
        if (!(maybeSemicolon is Token.Operator && maybeSemicolon.type == Token.Operator.Type.Semicolon)) {
            returnValue = parseExpression()
        }
        expectOperator(Token.Operator.Type.Semicolon)
        return AST.ReturnStatement(returnValue)
    }

    suspend fun parseIfStatement(): AST.IfStatement {
        expectKeyword(Token.Keyword.Type.If)
        expectOperator(Token.Operator.Type.LParen)
        val condition = parseExpression()
        expectOperator(Token.Operator.Type.RParen)
        val trueStatement = parseStatement()

        val maybeElseToken = peek(0)
        var falseStatement: AST.Statement? = null
        if (maybeElseToken is Token.Keyword && maybeElseToken.type == Token.Keyword.Type.Else) {
            expectKeyword(Token.Keyword.Type.Else)
            falseStatement = parseStatement()
        }
        return AST.IfStatement(
            condition,
            trueStatement,
            falseStatement
        )
    }

    suspend fun parseWhileStatement(): AST.WhileStatement {
        expectKeyword(Token.Keyword.Type.While)
        expectOperator(Token.Operator.Type.LParen)
        val loopCondition = parseExpression()
        expectOperator(Token.Operator.Type.RParen)
        val loopBodyStatement = parseStatement()

        return AST.WhileStatement(loopCondition, loopBodyStatement)
    }

    suspend fun parseEmptyStatement(): AST.EmptyStatement {
        expectOperator(Token.Operator.Type.Semicolon)
        return AST.EmptyStatement
    }

    suspend fun parseLocalVariableDeclarationStatement(): AST.LocalVariableDeclarationStatement {
        val type = parseType()
        val varName = expectIdentifier()
        val initializer = when (val nextToken = peek()) {
            is Token.Operator ->
                if (nextToken.type == Token.Operator.Type.Assign) {
                    next()
                    parseExpression()
                } else {
                    null
                }
            else -> null
        }
        expectOperator(Token.Operator.Type.Semicolon)
        return AST.LocalVariableDeclarationStatement(varName.name, type, initializer)
    }

    suspend fun parseExpressionStatement(): AST.ExpressionStatement {
        val expr = parseExpression()
        println("DEBUG in parseExpressionStatement. expr=$expr")
        expectOperator(Token.Operator.Type.Semicolon)
        return AST.ExpressionStatement(expr)
    }

    /**
     * TODO this can be ignored since we dont handle exceptions semantically?
     */
    suspend fun parseMethodRest() {
        expectKeyword(Token.Keyword.Type.Throws)
        expectIdentifier()
    }

    suspend fun parseParameters(): List<AST.Parameter> {

        return buildList<AST.Parameter> {
            add(parseParameter())
            var maybeCommaToken = peek(0)
            while (maybeCommaToken is Token.Operator && maybeCommaToken.type == Token.Operator.Type.Comma) {
                expectOperator(Token.Operator.Type.Comma) // never fails
                add(parseParameter())
                maybeCommaToken = peek(0)
            }
        }
    }

    suspend fun parseParameter(): AST.Parameter {
        val type = parseType()
        val ident = expectIdentifier()
        return AST.Parameter(
            ident.name,
            type
        )
    }

    suspend fun parseType(): Type {
        val basicType = parseBasicType()
        val maybeLeftBracket = peek(0)
        if (maybeLeftBracket is Token.Operator && maybeLeftBracket.type == Token.Operator.Type.LeftBracket) {
            print(basicType)
            return parseTypeArrayRecurse(basicType)
        }
        return basicType
    }

    suspend fun parseTypeArrayRecurse(basicType: Type): Type {
        expectOperator(Token.Operator.Type.LeftBracket)
        expectOperator(Token.Operator.Type.RightBracket)
        val maybeAnotherLBracket = peek(0)
        return if (maybeAnotherLBracket is Token.Operator && maybeAnotherLBracket.type == Token.Operator.Type.LeftBracket) {
            Type.Array(parseTypeArrayRecurse(basicType))
        } else Type.Array(basicType)
    }

    suspend fun parseBasicType(): Type {
        return when (val typeToken = next()) {
            is Token.Keyword -> {
                when (typeToken.type) {
                    Token.Keyword.Type.Int -> Type.Integer
                    Token.Keyword.Type.Boolean -> Type.Boolean
                    Token.Keyword.Type.Void -> Type.Void
                    else -> enterPanicMode()
                }
            }
            is Token.Identifier -> {
                Type.ClassType(typeToken.name)
            }
            else -> enterPanicMode()
        }
    }

    private suspend inline fun expectOperator(type: Token.Operator.Type): Token.Operator {
        val token = next()
        if (token !is Token.Operator) {
            println("expected operator, but got $token")
            enterPanicMode()
        }

        if (token.type == type)
            return token
        else {
            println("expected operator $type, but got $token")
            enterPanicMode()
        }
    }

    private suspend inline fun expectIdentifier(): Token.Identifier {
        val token = next()
        if (token !is Token.Identifier) {
            println("expected identifier, but found $token")
            enterPanicMode()
        }
        return token
    }

    private suspend inline fun expectKeyword(type: Token.Keyword.Type): Token.Keyword {
        val token = next()
        if (token !is Token.Keyword)
            enterPanicMode()

        if (token.type == type)
            return token
        else
            enterPanicMode()
    }
}
