package edu.kit.compiler.parser

import edu.kit.compiler.Token

object FirstFollowUtils {

    val firstSets: Array<AnchorSet> = arrayOf(
        // parse program:
        anchorSetOf()
    )
}

@JvmInline
value class AnchorSet(val tokens: Array<out Token>) {
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
        AnchorSet(setOf(*this.tokens, *anchorSet.tokens).toTypedArray())
}

/**
 * Construct an [AnchorSet] from a variadic array of tokens
 */
private fun anchorSetOf(vararg tokens: Token) = AnchorSet(tokens)
