package edu.kit.compiler.parser

import edu.kit.compiler.Token

object FirstFollowUtils {

    val emptyFirstSet = anchorSetOf()

    val firstSetBasicType = anchorSetOf(
        Token.Keyword(Token.Keyword.Type.Int),
        Token.Keyword(Token.Keyword.Type.Boolean),
        Token.Keyword(Token.Keyword.Type.Void),
        Token.Identifier(""),
    )
    val firstSetType = firstSetBasicType
    val firstSetParameter = firstSetType
    val firstSetParameters = firstSetParameter
    val firstSetMethodRest = anchorSetOf(Token.Keyword(Token.Keyword.Type.Throws))
    val firstSetMethod = anchorSetOf(Token.Keyword(Token.Keyword.Type.Public))
    val firstSetMainMethod = firstSetMethod
    val firstSetField = anchorSetOf(Token.Keyword(Token.Keyword.Type.Public))
    val firstSetClassMember = firstSetField + firstSetMethod + firstSetMainMethod

    val firstSetArrayAccess = anchorSetOf(Token.Operator(Token.Operator.Type.LeftBracket))
    val firstSetFieldAccess = anchorSetOf(Token.Operator(Token.Operator.Type.Dot))
    val firstSetMethodInvocation = anchorSetOf(Token.Operator(Token.Operator.Type.Dot))
    val firstSetPostfixOp = firstSetMethodInvocation + firstSetFieldAccess + firstSetArrayAccess
    val firstSetClassDeclaration = anchorSetOf(Token.Keyword(Token.Keyword.Type.Class))
    val firstSetProgram = firstSetClassDeclaration

    val firstSetNewObjectExpression = anchorSetOf(Token.Keyword(Token.Keyword.Type.New))
    val firstSetNewArrayExpression = anchorSetOf(Token.Keyword(Token.Keyword.Type.New))
    val firstSetPrimaryExpression =
        anchorSetOf(
            Token.Keyword(Token.Keyword.Type.Null),
            Token.Keyword(Token.Keyword.Type.False),
            Token.Keyword(Token.Keyword.Type.True),
            Token.Literal(""),
            Token.Identifier(""),
            Token.Keyword(Token.Keyword.Type.This),
            Token.Operator(Token.Operator.Type.LParen)
        ) + firstSetNewArrayExpression + firstSetNewObjectExpression
    val firstSetPostfixExpression = firstSetPrimaryExpression
    val firstSetUnaryExpression = anchorSetOf(
        Token.Operator(Token.Operator.Type.Not),
        Token.Operator(Token.Operator.Type.Minus)
    ) + firstSetPostfixExpression
    val firstSetTypeArrayRecurse = anchorSetOf(Token.Operator(Token.Operator.Type.LeftBracket))
    val firstSetMultiplicativeExpression = firstSetUnaryExpression
    val firstSetAdditiveExpression = firstSetMultiplicativeExpression
    val firstSetRelationalExpression = firstSetAdditiveExpression
    val firstSetEqualityExpression = firstSetRelationalExpression
    val firstSetLogicalAndExpression = firstSetEqualityExpression
    val firstSetLogicalOrExpression = firstSetLogicalAndExpression
    val firstSetAssignmentExpression = firstSetLogicalOrExpression
    val firstSetExpression = firstSetAssignmentExpression

    val firstSetReturnStatement = anchorSetOf(Token.Keyword(Token.Keyword.Type.Return))
    val firstSetExpressionStatement = firstSetExpression
    val firstSetIfStatement = anchorSetOf(Token.Keyword(Token.Keyword.Type.If))
    val firstSetWhileStatement = anchorSetOf(Token.Keyword(Token.Keyword.Type.While))
    val firstSetEmptyStatement = anchorSetOf(Token.Operator(Token.Operator.Type.Semicolon))
    val firstSetLocalVariableDeclarationStatement = firstSetType
    val firstSetBlock = anchorSetOf(Token.Operator(Token.Operator.Type.LeftBrace))
    val firstSetStatement =
        firstSetBlock + firstSetEmptyStatement + firstSetIfStatement + firstSetExpressionStatement + firstSetWhileStatement + firstSetReturnStatement
    val firstSetBlockStatement = firstSetStatement + firstSetLocalVariableDeclarationStatement
}

@JvmInline
value class AnchorSet(val tokens: Set<Token>) {
    /**
     * @param token a token to check against this [AnchorSet]
     * @return true, if the given token type is within this anchor set.
     */
    fun isInSet(token: Token): Boolean {
        return when (token) {
            is Token.Identifier -> tokens.any { it is Token.Identifier }
            is Token.Literal -> tokens.any { it is Token.Literal }
            Token.Eof -> token in tokens
            is Token.ErrorToken -> false
            is Token.Keyword -> token.type in tokens.mapNotNull { (it as? Token.Keyword)?.type }
            is Token.Operator -> token.type in tokens.mapNotNull { (it as? Token.Operator)?.type }
            is Token.Whitespace -> error("whitespace tokens should not be checked against anchor sets")
            is Token.Comment -> error("comment tokens should not be checked against anchor sets")
        }
    }

    /**
     * Union operator for two [AnchorSets][AnchorSet]
     */
    operator fun plus(anchorSet: AnchorSet): AnchorSet = AnchorSet(this.tokens.union(anchorSet.tokens))

    /**
     * `in` operator for this set
     */
    operator fun contains(t: Token): Boolean = t in tokens
}

/**
 * Construct an [AnchorSet] from a variadic array of tokens
 */
fun anchorSetOf(vararg tokens: Token) = AnchorSet(tokens.toSet())
