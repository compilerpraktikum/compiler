package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.wrapper.IdentityType
import edu.kit.compiler.wrapper.Of


/**
 * Represents the type of an expression, that is synthesized from its child nodes
 * We use a value class there, to make it harder to mixing up [SynthesizedType] and [InducedType]
 */
@JvmInline
value class SynthesizedType(val synthesizedType: IdentityType)

/**
 * Represents a [Node] annotated with its synthesized type
 */
typealias SynthesizedTyped<Node> = Annotated<SynthesizedType, Node>

/**
 * Represents the type of an expression, that is induced by its environment
 * We use a value class there, to make it harder to mixing up [SynthesizedType] and [InducedType]
 */
@JvmInline
value class InducedType(val inducedType: IdentityType)


/**
 * Represents a [Node] annotated with its induced type
 */
typealias InducedTyped<Node> = Annotated<InducedType, Node>

/**
 * Represents a [Node] annotated with its induced and synthesized type
 * if the induced and synthesized type differ, it is considered a type-error
 */
typealias LenientTyped<Node> = Compose<InducedTyped<Of>, SynthesizedTyped<Of>, Node>

val <Node> LenientTyped<Node>.inducedType: InducedType get() = this.unCompose.into().ann
val <Node> LenientTyped<Node>.synthesizedType: SynthesizedType get() = this.unCompose.into().node.into().ann
val <Node> LenientTyped<Node>.astNode : Node get() = this.unCompose.into().node.into().node
