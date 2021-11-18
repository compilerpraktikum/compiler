package edu.kit.compiler.wrapper

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.wrappers.Annotated
import edu.kit.compiler.wrapper.wrappers.Compose
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.UnwrappableAnnotated
import edu.kit.compiler.wrapper.wrappers.into
import edu.kit.compiler.wrapper.wrappers.mapClassW
import edu.kit.compiler.wrapper.wrappers.mapExprW
import edu.kit.compiler.wrapper.wrappers.mapMember
import edu.kit.compiler.wrapper.wrappers.mapMethodW
import edu.kit.compiler.wrapper.wrappers.mapOtherW
import edu.kit.compiler.wrapper.wrappers.mapStmt
import edu.kit.compiler.wrapper.wrappers.unwrapOr

/**
 * Wrapper stack that the parser outputs.
 * It consists of [Lenient] AST Node `A`, that have a
 * [SourceRange] associated with them (using [Positioned])
 */
typealias Parsed<A> = Compose<Positioned<Of>, Lenient<Of>, A>

// -------------------------- validate ----------------------------//
private fun <Ann, Node> Annotated<Ann, Node>.unwrapAnnotated() = this.unwrap(UnwrappableAnnotated())

fun Parsed<ParsedProgram>.validate(): IdentityProgram? =
    this.unCompose
        .into()
        .unwrapAnnotated()
        .into()
        .unwrapOr { return null }
        .validate()

fun ParsedProgram.validate(): IdentityProgram? =
    this.mapClassW { parsed ->
        parsed
            .into()
            .unCompose
            .into()
            .unwrapAnnotated()
            .into()
            .unwrapOr { return null }
            .validate()
            .let {
                it ?: return null
            }.let {
                Identity(it)
            }
    }

fun ParsedClassDeclaration.validate(): IdentityClassDeclaration? =
    this.mapMethodW { parsed ->
        parsed
            .into()
            .unCompose
            .into()
            .unwrapAnnotated()
            .into()
            .unwrapOr { return null }
            .validate()
            .let {
                it ?: return null
            }.let {
                Identity(it)
            }
    }

fun ParsedClassMember.validate(): IdentityClassMember? =
    this.mapMember(
        { parsed ->
            parsed.into()
                .unCompose
                .into()
                .unwrapAnnotated()
                .into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
                .let { Identity(it) }
        },
        { parsed ->
            parsed.into()
                .unCompose
                .into()
                .unwrapAnnotated()
                .into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
                .let { Identity(it) }
        },
        { parsed ->
            parsed
                .into()
                .unCompose
                .into()
                .unwrapAnnotated()
                .into()
                .unwrapOr { return null }
                .validate()
                .let { it ?: return null }
                .let { Identity(it) }
        }
    )

fun ParsedBlock.validate(): IdentityBlock? =
    this.mapStmt { parsed ->
        parsed.into()
            .unCompose
            .into()
            .unwrapAnnotated()
            .into()
            .unwrapOr { return null }
            .into()
            .validate()
            .let { it ?: return null }
            .let {
                Identity(it)
            }
    }

fun ParsedBlockStatement.validate(): IdentityBlockStatement? =
    when (this) {
        is AST.LocalVariableDeclarationStatement ->
            AST.LocalVariableDeclarationStatement(
                name,
                type.into().unCompose.into().unwrapAnnotated().into().unwrapOr { return null }.into().validate()
                    .let { it ?: return null }.let
                    { Identity(it) },
                initializer?.let { justInitializer ->
                    justInitializer.into().unCompose.into().unwrapAnnotated().into().unwrapOr { return null }.into()
                        .validate()
                        .let { it ?: return null }.let { Identity(it) }
                }
            )
        is AST.StmtWrapper -> AST.StmtWrapper(
            this.statement.mapStmt(
                { parsed ->
                    parsed
                        .into()
                        .unCompose
                        .into()
                        .unwrapAnnotated()
                        .into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                        .let { Identity(it) }
                },
                { parsed ->
                    parsed
                        .into()
                        .unCompose
                        .into()
                        .unwrapAnnotated()
                        .into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                        .let { Identity(it) }
                }, { parsed ->
                parsed.into().unCompose.into().unwrapAnnotated().into().unwrapOr { return null }.into().validate()
                    .let { it ?: return null }.let { Identity(it) }
            }
            )
        )
    }

fun ParsedStatement.validate(): IdentityStatement? =
    this.mapStmt(
        { parsed ->
            parsed.into()
                .unCompose
                .into()
                .unwrapAnnotated()
                .into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
                .let { Identity(it) }
        },
        { parsed ->
            parsed.into()
                .unCompose
                .into()
                .unwrapAnnotated()
                .into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
                .let { Identity(it) }
        }, { parsed ->
        parsed.into()
            .unCompose
            .into()
            .unwrapAnnotated()
            .into()
            .unwrapOr { return null }
            .into()
            .validate()
            .let { it ?: return null }
            .let { Identity(it) }
    }
    )

fun ParsedExpression.validate(): IdentityExpression? =
    this.mapExprW({ parsed ->
        parsed
            .into().unCompose.into().unwrapAnnotated().into()
            .unwrapOr { return null }
            .into()
            .validate()
            .let { it ?: return null }
            .let { Identity(it) }
    }, { parsed ->
        parsed
            .into().unCompose.into().unwrapAnnotated().into()
            .unwrapOr { return null }
            .into()
            .validate()
            .let { it ?: return null }
            .let { Identity(it) }
    })

fun ParsedArrayType.validate(): IdentityArrayType? =
    this
        .elementType
        .into().unCompose.into().unwrapAnnotated().into()
        .unwrapOr { return null }
        .into()
        .validate()
        .let { it ?: return null }
        .let { Identity(it) }
        .let { Type.Array.ArrayType(it) }

fun ParsedParameter.validate(): IdentityParameter? =
    this.mapOtherW { parsed ->
        parsed.into()
            .unCompose
            .into()
            .unwrapAnnotated()
            .into()
            .unwrapOr { return null }
            .into()
            .validate()
            .let { it ?: return null }
            .let { Identity(it) }
    }

fun ParsedType.validate(): IdentityType? = this.mapOtherW {
    it.into()
        .unCompose
        .into()
        .unwrapAnnotated()
        .into()
        .unwrapOr { return null }
        .into()
        .validate()
        .let { it ?: return null }
        .let { Identity(it) }
}
