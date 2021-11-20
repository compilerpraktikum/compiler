package edu.kit.compiler.wrapper

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.wrappers.Compose
import edu.kit.compiler.wrapper.wrappers.FunctorAnnotated
import edu.kit.compiler.wrapper.wrappers.FunctorCompose
import edu.kit.compiler.wrapper.wrappers.FunctorLenient
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.into
import edu.kit.compiler.wrapper.wrappers.mapClassW
import edu.kit.compiler.wrapper.wrappers.mapExprW
import edu.kit.compiler.wrapper.wrappers.mapMember
import edu.kit.compiler.wrapper.wrappers.mapMethodW
import edu.kit.compiler.wrapper.wrappers.mapOtherW
import edu.kit.compiler.wrapper.wrappers.mapStmt
import edu.kit.compiler.wrapper.wrappers.mapValue
import edu.kit.compiler.wrapper.wrappers.unwrapAnnotated
import edu.kit.compiler.wrapper.wrappers.unwrapOr

/**
 * Wrapper stack that the parser outputs.
 * It consists of [Lenient] AST Node `A`, that have a
 * [SourceRange] associated with them (using [Positioned])
 */
typealias Parsed<A> = Compose<Positioned<Of>, Lenient<Of>, A>

// -------------------------- Instances ----------------------------//

val ParsedFunctor = FunctorCompose<Positioned<Of>, Lenient<Of>>(FunctorAnnotated(), FunctorLenient)

fun <A, B> Kind<Parsed<Of>, A>.fmapParsed(f: (A) -> B): Parsed<B> =
    ParsedFunctor
        .functorMap(f, this)
        .into()

// -------------------------- validate ----------------------------//

fun Parsed<ParsedProgram>.validate(): PositionedProgram? =
    this.unCompose
        .into()
        .unwrapAnnotated()
        .into()
        .unwrapOr { return null }
        .validate()

fun ParsedProgram.validate(): PositionedProgram? =
    this.mapClassW { parsed ->
        parsed
            .into()
            .unCompose
            .mapValue {
                it.into()
                    .unwrapOr { return null }
                    .validate()
                    .let {
                        it ?: return null
                    }
            }
    }

fun ParsedClassDeclaration.validate(): PositionedClassDeclaration? =
    this.mapMethodW { parsed ->
        parsed
            .into()
            .unCompose
            .mapValue {
                it.into()
                    .unwrapOr { return null }
                    .validate()
                    .let {
                        it ?: return null
                    }
            }
    }

fun ParsedClassMember.validate(): PositionedClassMember? =
    this.mapMember(
        { parsed ->
            parsed.into()
                .unCompose
                .mapValue {
                    it.into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                }
        },
        { parsed ->
            parsed.into()
                .unCompose
                .mapValue {
                    it.into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                }
        },
        { parsed ->
            parsed
                .into()
                .unCompose
                .mapValue {
                    it.into()
                        .unwrapOr { return null }
                        .validate()
                        .let { it ?: return null }
                }
        }
    )

fun ParsedBlock.validate(): PositionedBlock? =
    this.mapStmt { parsed ->
        parsed.into()
            .unCompose
            .mapValue {
                it.into()
                    .unwrapOr { return null }
                    .into()
                    .validate()
                    .let { it ?: return null }
            }
    }

fun ParsedBlockStatement.validate(): PositionedBlockStatement? =
    when (this) {
        is AST.LocalVariableDeclarationStatement ->
            AST.LocalVariableDeclarationStatement(
                name,
                type.into().unCompose.mapValue {
                    it.into().unwrapOr { return null }.into().validate()
                        .let { it ?: return null }
                },
                initializer?.let { justInitializer ->
                    justInitializer.into().unCompose.mapValue {
                        it.into().unwrapOr { return null }.into()
                            .validate()
                            .let { it ?: return null }
                    }
                }
            )
        is AST.StmtWrapper -> AST.StmtWrapper(
            this.statement.mapStmt(
                { parsed ->
                    parsed
                        .into()
                        .unCompose
                        .mapValue {
                            it.into()
                                .unwrapOr { return null }
                                .into()
                                .validate()
                                .let { it ?: return null }
                        }
                },
                { parsed ->
                    parsed
                        .into()
                        .unCompose
                        .mapValue {
                            it.into()
                                .unwrapOr { return null }
                                .into()
                                .validate()
                                .let { it ?: return null }
                        }
                }, { parsed ->
                parsed.into().unCompose.mapValue {
                    it.into().unwrapOr { return null }.into().validate()
                        .let { it ?: return null }
                }
            }
            )
        )
    }

fun ParsedStatement.validate(): PositionedStatement? =
    this.mapStmt(
        { parsed ->
            parsed.into()
                .unCompose
                .mapValue {
                    it.into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                }
        },
        { parsed ->
            parsed.into()
                .unCompose
                .mapValue {
                    it.into()
                        .unwrapOr { return null }
                        .into()
                        .validate()
                        .let { it ?: return null }
                }
        }, { parsed ->
        parsed.into()
            .unCompose
            .mapValue {
                it.into()
                    .unwrapOr { return null }
                    .into()
                    .validate()
                    .let { it ?: return null }
            }
    }
    )

fun ParsedExpression.validate(): PositionedExpression? =
    this.mapExprW({ parsed ->
        parsed
            .into().unCompose.mapValue {
                it.into()
                    .unwrapOr { return null }
                    .into()
                    .validate()
                    .let { it ?: return null }
            }
    }, { parsed ->
        parsed
            .into().unCompose.into().mapValue {
                it.into()
                    .unwrapOr { return null }
                    .into()
                    .validate()
                    .let { it ?: return null }
            }
    })

fun ParsedArrayType.validate(): PositionedArrayType? =
    this
        .elementType
        .into().unCompose.into().mapValue {
            it.into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
        }.let { Type.Array.ArrayType(it) }

fun ParsedParameter.validate(): PositionedParameter? =
    this.mapOtherW { parsed ->
        parsed.into()
            .unCompose
            .mapValue {
                it.into()
                    .unwrapOr { return null }
                    .into()
                    .validate()
                    .let { it ?: return null }
            }
    }

fun ParsedType.validate(): PositionedType? = this.mapOtherW {
    it.into()
        .unCompose
        .mapValue {
            it.into()
                .unwrapOr { return null }
                .into()
                .validate()
                .let { it ?: return null }
        }
}
