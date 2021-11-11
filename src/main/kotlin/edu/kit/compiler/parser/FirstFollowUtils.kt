package edu.kit.compiler.parser

import edu.kit.compiler.Token

object FirstFollowUtils {

    val firstSetProgram = anchorSetOf(Token.Keyword(Token.Keyword.Type.Class))
    val firstSetClassDeclaration = firstSetProgram
    val firstSetClassMember = anchorSetOf(Token.Keyword(Token.Keyword.Type.Public))
    val firstSetMainMethod = anchorSetOf(Token.Keyword(Token.Keyword.Type.Static))

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
    operator fun plus(anchorSet: AnchorSet): AnchorSet =
        AnchorSet(this.tokens.union(anchorSet.tokens))
}

/**
 * Construct an [AnchorSet] from a variadic array of tokens
 */
private fun anchorSetOf(vararg tokens: Token) = AnchorSet(tokens.toSet())
