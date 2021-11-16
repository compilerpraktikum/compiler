package edu.kit.compiler.parser

import edu.kit.compiler.Token

object FirstFollowUtils {

    val allExpressionOperators = anchorSetOf(
        Token.Operator(Token.Operator.Type.NoEq),
        Token.Operator(Token.Operator.Type.Not),
        Token.Operator(Token.Operator.Type.LParen),
        Token.Operator(Token.Operator.Type.RParen),
        Token.Operator(Token.Operator.Type.MulAssign),
        Token.Operator(Token.Operator.Type.Mul),
        Token.Operator(Token.Operator.Type.PlusPlus),
        Token.Operator(Token.Operator.Type.PlusAssign),
        Token.Operator(Token.Operator.Type.Plus),
        Token.Operator(Token.Operator.Type.MinusAssign),
        Token.Operator(Token.Operator.Type.MinusMinus),
        Token.Operator(Token.Operator.Type.Minus),
        Token.Operator(Token.Operator.Type.DivAssign),
        Token.Operator(Token.Operator.Type.Div),
        Token.Operator(Token.Operator.Type.LeftShiftAssign),
        Token.Operator(Token.Operator.Type.LeftShift),
        Token.Operator(Token.Operator.Type.LtEq),
        Token.Operator(Token.Operator.Type.Lt),
        Token.Operator(Token.Operator.Type.Eq),
        Token.Operator(Token.Operator.Type.Assign),
        Token.Operator(Token.Operator.Type.GtEq),
        Token.Operator(Token.Operator.Type.RightShiftSEAssign),
        Token.Operator(Token.Operator.Type.RightShiftAssign),
        Token.Operator(Token.Operator.Type.RightShift),
        Token.Operator(Token.Operator.Type.RightShiftSE),
        Token.Operator(Token.Operator.Type.Gt),
        Token.Operator(Token.Operator.Type.ModAssign),
        Token.Operator(Token.Operator.Type.Mod),
        Token.Operator(Token.Operator.Type.AndAssign),
        Token.Operator(Token.Operator.Type.And),
        Token.Operator(Token.Operator.Type.BitAnd),
        Token.Operator(Token.Operator.Type.XorAssign),
        Token.Operator(Token.Operator.Type.Xor),
        Token.Operator(Token.Operator.Type.BitNot),
        Token.Operator(Token.Operator.Type.OrAssign),
        Token.Operator(Token.Operator.Type.Or),
        Token.Operator(Token.Operator.Type.BitOr)
    )

    val firstSetBasicType = anchorSetOf(
        Token.Keyword(Token.Keyword.Type.Int),
        Token.Keyword(Token.Keyword.Type.Boolean),
        Token.Keyword(Token.Keyword.Type.Void),
        Token.Identifier.Placeholder,
    )
    val firstSetType = firstSetBasicType
    val firstSetParameter = firstSetType
    val firstSetMethod = anchorSetOf(Token.Keyword(Token.Keyword.Type.Public))
    val firstSetMainMethod = firstSetMethod
    val firstSetField = anchorSetOf(Token.Keyword(Token.Keyword.Type.Public))
    val firstSetClassMember = firstSetField + firstSetMethod + firstSetMainMethod
    val firstSetClassMembers = firstSetClassMember
    val firstSetFieldMethodPrefix = anchorSetOf(
        Token.Keyword(Token.Keyword.Type.Int),
        Token.Keyword(Token.Keyword.Type.Boolean),
        Token.Keyword(Token.Keyword.Type.Void),
        Token.Identifier.Placeholder
    )
    val firstSetMainMethodPrefix = anchorSetOf(Token.Keyword(Token.Keyword.Type.Static))

    val firstSetArrayAccess = anchorSetOf(Token.Operator(Token.Operator.Type.LeftBracket))
    val firstSetFieldAccess = anchorSetOf(Token.Operator(Token.Operator.Type.Dot))
    val firstSetMethodInvocation = anchorSetOf(Token.Operator(Token.Operator.Type.Dot))
    val firstSetPostfixOp = firstSetMethodInvocation + firstSetFieldAccess + firstSetArrayAccess

    val firstSetNewObjectExpression = anchorSetOf(Token.Keyword(Token.Keyword.Type.New))
    val firstSetNewArrayExpression = anchorSetOf(Token.Keyword(Token.Keyword.Type.New))
    val firstSetPrimaryExpression =
        anchorSetOf(
            Token.Keyword(Token.Keyword.Type.Null),
            Token.Keyword(Token.Keyword.Type.False),
            Token.Keyword(Token.Keyword.Type.True),
            Token.Literal(""),
            Token.Identifier.Placeholder,
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
    val firstSetArguments = firstSetExpression

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

/**
 * An inline type for token-sets that are used as anchor sets for the [Parser]. The type provides operators to
 * [combine][plus] two sets (providing an [AnchorUnion] as a lazy set) and [contains]
 */
@JvmInline
value class AnchorSet(internal val tokens: Set<Token>) {
    /**
     * Check whether a given token is covered by this anchor set. This checks for token types, but it does not check for
     * token contents like literal values.
     *
     * @param token a token to check against this [AnchorSet]
     * @return true, if the given token type is within this anchor set.
     */
    private fun isInSet(token: Token): Boolean {
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
     * Union operator for two [AnchorSets][AnchorSet] that generates a lazy union of both sets
     */
    operator fun plus(anchorSet: AnchorSet) = AnchorUnion { AnchorSet(this.tokens.union(anchorSet.tokens)) }

    /**
     * Union operator for an [AnchorSet] and a lazy [AnchorUnion] that generates another lazy union of both sets
     */
    operator fun plus(anchorUnion: AnchorUnion) =
        AnchorUnion { AnchorSet(this.tokens.union(anchorUnion.provide().tokens)) }

    /**
     * `in` operator for this set
     */
    operator fun contains(t: Token): Boolean = isInSet(t)

    /**
     * Converts this [AnchorSet] instance into an [AnchorUnion] that provides the same content
     */
    fun intoUnion(): AnchorUnion = AnchorUnion { this@AnchorSet }
}

/**
 * An inline class that provides a lazily evaluated union of multiple [AnchorSets][AnchorSet].
 */
@JvmInline
value class AnchorUnion(private val lazyTokenSet: () -> AnchorSet) {

    /**
     * Evaluate the union and return it as an [AnchorSet]
     */
    internal fun provide() = lazyTokenSet.invoke()

    /**
     * Union operator for two [AnchorSets][AnchorSet] that generates a lazy union of both sets
     */
    operator fun plus(anchorSet: AnchorSet) = AnchorUnion { AnchorSet(this.provide().tokens.union(anchorSet.tokens)) }

    /**
     * Union operator for an [AnchorSet] and a lazy [AnchorUnion] that generates another lazy union of both sets
     */
    operator fun plus(anchorUnion: AnchorUnion) =
        AnchorUnion { AnchorSet(this.provide().tokens.union(anchorUnion.provide().tokens)) }
}

/**
 * Construct an [AnchorSet] from a variadic array of tokens
 */
fun anchorSetOf(vararg tokens: Token) = AnchorSet(tokens.toSet())
