package edu.kit.compiler.wrapper

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.LenientTyped
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.SynthesizedTyped

/**
 * This is just a file full of aliases for different wrapper applications, that we might use
 * Not all the aliases are used, but having the complete list makes it easy to add new aliases, just
 * by copy and pasting the whole block and replacing the wrapper name
 */

/************************************************
 ** Lenient
 ************************************************/
/** Type containing [Lenient]s */
typealias LenientType = Type<Lenient<Of>>
/** Array containing [Lenient]s */
typealias LenientArray = Array<Lenient<Of>>
/** ArrayType.ArrayType containing [Lenient]s */
typealias LenientArrayType = Type.Array.ArrayType<Lenient<Of>>

/** Program containing [Lenient]s */
typealias LenientProgram = AST.Program<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** ClassDeclaration containing [Lenient]s */
typealias LenientClassDeclaration = AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>

/** ClassMember containing [Lenient]s */
typealias LenientClassMember = AST.ClassMember<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** Field containing [Lenient]s */
typealias LenientField = AST.Field<Lenient<Of>>
/** Method containing [Lenient]s */
typealias LenientMethod = AST.Method<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** MainMethod containing [Lenient]s */
typealias LenientMainMethod = AST.MainMethod<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** Parameter containing [Lenient]s */
typealias LenientParameter = AST.Parameter<Lenient<Of>>

/** BlockStatement containing [Lenient]s */
typealias LenientBlockStatement = AST.BlockStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** LocalVariableDeclaration containing [Lenient]s */
typealias LenientLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Lenient<Of>, Lenient<Of>>
/** StmtWrapper containing [Lenient]s */
typealias LenientStmtWrapper = AST.StmtWrapper<Lenient<Of>, Lenient<Of>, Lenient<Of>>

/** Statement containing [Lenient]s */
typealias LenientStatement = AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** Block containing [Lenient]s */
typealias LenientBlock = AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** IfStatement containing [Lenient]s */
typealias LenientIfStatement = AST.IfStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** WhileStatement containing [Lenient]s */
typealias LenientWhileStatement = AST.WhileStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
/** ReturnStatement containing [Lenient]s */
typealias LenientReturnStatement = AST.ReturnStatement<Lenient<Of>, Lenient<Of>>
/** ExpressionStatement containing [Lenient]s */
typealias LenientExpressionStatement = AST.ExpressionStatement<Lenient<Of>, Lenient<Of>>

/** Expression containing [Lenient]s */
typealias LenientExpression = AST.Expression<Lenient<Of>, Lenient<Of>>
/** BinaryExpression containing [Lenient]s */
typealias LenientBinaryExpression = AST.BinaryExpression<Lenient<Of>, Lenient<Of>>
/** UnaryExpression containing [Lenient]s */
typealias LenientUnaryExpression = AST.UnaryExpression<Lenient<Of>, Lenient<Of>>
/** MethodInvocationExpression containing [Lenient]s */
typealias LenientMethodInvocationExpression = AST.MethodInvocationExpression<Lenient<Of>, Lenient<Of>>
/** FieldAccessExpression containing [Lenient]s */
typealias LenientFieldAccessExpression = AST.FieldAccessExpression<Lenient<Of>, Lenient<Of>>
/** ArrayAccessExpression containing [Lenient]s */
typealias LenientArrayAccessExpression = AST.ArrayAccessExpression<Lenient<Of>, Lenient<Of>>
/** IdentifierExpression containing [Lenient]s */
typealias LenientIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [Lenient]s */
typealias LenientLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [Lenient]s */
typealias LenientNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [Lenient]s */
typealias LenientNewArrayExpression = AST.NewArrayExpression<Lenient<Of>, Lenient<Of>>

/************************************************
 ** Identity
 ************************************************/
/** Type containing [Identity]s */
typealias IdentityType = Type<Identity<Of>>
/** Array containing [Identity]s */
typealias IdentityArray = Array<Identity<Of>>
/** ArrayType containing [Identity]s */
typealias IdentityArrayType = Type.Array.ArrayType<Identity<Of>>

/** Program containing [Identity]s */
typealias IdentityProgram = AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>
/** ClassDeclaration containing [Identity]s */
typealias IdentityClassDeclaration = AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>

/** ClassMember containing [Identity]s */
typealias IdentityClassMember = AST.ClassMember<Identity<Of>, Identity<Of>, Identity<Of>>
/** Field containing [Identity]s */
typealias IdentityField = AST.Field<Identity<Of>>
/** Method containing [Identity]s */
typealias IdentityMethod = AST.Method<Identity<Of>, Identity<Of>, Identity<Of>>
/** MainMethod containing [Identity]s */
typealias IdentityMainMethod = AST.MainMethod<Identity<Of>, Identity<Of>, Identity<Of>>
/** Parameter containing [Identity]s */
typealias IdentityParameter = AST.Parameter<Identity<Of>>

/** BlockStatement containing [Identity]s */
typealias IdentityBlockStatement = AST.BlockStatement<Identity<Of>, Identity<Of>, Identity<Of>>
/** LocalVariableDeclaration containing [Identity]s */
typealias IdentityLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>
/** StmtWrapper containing [Identity]s */
typealias IdentityStmtWrapper = AST.StmtWrapper<Identity<Of>, Identity<Of>, Identity<Of>>

/** Statement containing [Identity]s */
typealias IdentityStatement = AST.Statement<Identity<Of>, Identity<Of>, Identity<Of>>
/** Block containing [Identity]s */
typealias IdentityBlock = AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>
/** IfStatement containing [Identity]s */
typealias IdentityIfStatement = AST.IfStatement<Identity<Of>, Identity<Of>, Identity<Of>>
/** WhileStatement containing [Identity]s */
typealias IdentityWhileStatement = AST.WhileStatement<Identity<Of>, Identity<Of>, Identity<Of>>
/** ReturnStatement containing [Identity]s */
typealias IdentityReturnStatement = AST.ReturnStatement<Identity<Of>, Identity<Of>>
/** ExpressionStatement containing [Identity]s */
typealias IdentityExpressionStatement = AST.ExpressionStatement<Identity<Of>, Identity<Of>>

/** Expression containing [Identity]s */
typealias IdentityExpression = AST.Expression<Identity<Of>, Identity<Of>>
/** BinaryExpression containing [Identity]s */
typealias IdentityBinaryExpression = AST.BinaryExpression<Identity<Of>, Identity<Of>>
/** UnaryExpression containing [Identity]s */
typealias IdentityUnaryExpression = AST.UnaryExpression<Identity<Of>, Identity<Of>>
/** MethodInvocationExpression containing [Identity]s */
typealias IdentityMethodInvocationExpression = AST.MethodInvocationExpression<Identity<Of>, Identity<Of>>
/** FieldAccessExpression containing [Identity]s */
typealias IdentityFieldAccessExpression = AST.FieldAccessExpression<Identity<Of>, Identity<Of>>
/** ArrayAccessExpression containing [Identity]s */
typealias IdentityArrayAccessExpression = AST.ArrayAccessExpression<Identity<Of>, Identity<Of>>
/** IdentifierExpression containing [Identity]s */
typealias IdentityIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [Identity]s */
typealias IdentityLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [Identity]s */
typealias IdentityNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [Identity]s */
typealias IdentityNewArrayExpression = AST.NewArrayExpression<Identity<Of>, Identity<Of>>

/************************************************
 ** Positioned
 ************************************************/
/** Type containing [Positioned]s */
typealias PositionedType = Type<Positioned<Of>>
/** Array containing [Positioned]s */
typealias PositionedArray = Array<Positioned<Of>>
/** ArrayType containing [Positioned]s */
typealias PositionedArrayType = Type.Array.ArrayType<Positioned<Of>>

/** Program containing [Positioned]s */
typealias PositionedProgram = AST.Program<Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** ClassDeclaration containing [Positioned]s */
typealias PositionedClassDeclaration = AST.ClassDeclaration<Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>

/** ClassMember containing [Positioned]s */
typealias PositionedClassMember = AST.ClassMember<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** Field containing [Positioned]s */
typealias PositionedField = AST.Field<Positioned<Of>>
/** Method containing [Positioned]s */
typealias PositionedMethod = AST.Method<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** MainMethod containing [Positioned]s */
typealias PositionedMainMethod = AST.MainMethod<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** Parameter containing [Positioned]s */
typealias PositionedParameter = AST.Parameter<Positioned<Of>>

/** BlockStatement containing [Positioned]s */
typealias PositionedBlockStatement = AST.BlockStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** LocalVariableDeclaration containing [Positioned]s */
typealias PositionedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Positioned<Of>, Positioned<Of>>
/** StmtWrapper containing [Positioned]s */
typealias PositionedStmtWrapper = AST.StmtWrapper<Positioned<Of>, Positioned<Of>, Positioned<Of>>

/** Statement containing [Positioned]s */
typealias PositionedStatement = AST.Statement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** Block containing [Positioned]s */
typealias PositionedBlock = AST.Block<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** IfStatement containing [Positioned]s */
typealias PositionedIfStatement = AST.IfStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** WhileStatement containing [Positioned]s */
typealias PositionedWhileStatement = AST.WhileStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
/** ReturnStatement containing [Positioned]s */
typealias PositionedReturnStatement = AST.ReturnStatement<Positioned<Of>, Positioned<Of>>
/** ExpressionStatement containing [Positioned]s */
typealias PositionedExpressionStatement = AST.ExpressionStatement<Positioned<Of>, Positioned<Of>>

/** Expression containing [Positioned]s */
typealias PositionedExpression = AST.Expression<Positioned<Of>, Positioned<Of>>
/** BinaryExpression containing [Positioned]s */
typealias PositionedBinaryExpression = AST.BinaryExpression<Positioned<Of>, Positioned<Of>>
/** UnaryExpression containing [Positioned]s */
typealias PositionedUnaryExpression = AST.UnaryExpression<Positioned<Of>, Positioned<Of>>
/** MethodInvocationExpression containing [Positioned]s */
typealias PositionedMethodInvocationExpression = AST.MethodInvocationExpression<Positioned<Of>, Positioned<Of>>
/** FieldAccessExpression containing [Positioned]s */
typealias PositionedFieldAccessExpression = AST.FieldAccessExpression<Positioned<Of>, Positioned<Of>>
/** ArrayAccessExpression containing [Positioned]s */
typealias PositionedArrayAccessExpression = AST.ArrayAccessExpression<Positioned<Of>, Positioned<Of>>
/** IdentifierExpression containing [Positioned]s */
typealias PositionedIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [Positioned]s */
typealias PositionedLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [Positioned]s */
typealias PositionedNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [Positioned]s */
typealias PositionedNewArrayExpression = AST.NewArrayExpression<Positioned<Of>, Positioned<Of>>

/************************************************
 ** Parsed
 ************************************************/
/** Type containing [Parsed]s */
typealias ParsedType = Type<Parsed<Of>>
/** Array containing [Parsed]s */
typealias ParsedArray = Array<Parsed<Of>>
/** ArrayType containing [Parsed]s */
typealias ParsedArrayType = Type.Array.ArrayType<Parsed<Of>>

/** Program containing [Parsed]s */
typealias ParsedProgram = AST.Program<Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** ClassDeclaration containing [Parsed]s */
typealias ParsedClassDeclaration = AST.ClassDeclaration<Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>>

/** ClassMember containing [Parsed]s */
typealias ParsedClassMember = AST.ClassMember<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** Field containing [Parsed]s */
typealias ParsedField = AST.Field<Parsed<Of>>
/** Method containing [Parsed]s */
typealias ParsedMethod = AST.Method<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** MainMethod containing [Parsed]s */
typealias ParsedMainMethod = AST.MainMethod<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** Parameter containing [Parsed]s */
typealias ParsedParameter = AST.Parameter<Parsed<Of>>

/** BlockStatement containing [Parsed]s */
typealias ParsedBlockStatement = AST.BlockStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** LocalVariableDeclaration containing [Parsed]s */
typealias ParsedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Parsed<Of>, Parsed<Of>>
/** StmtWrapper containing [Parsed]s */
typealias ParsedStmtWrapper = AST.StmtWrapper<Parsed<Of>, Parsed<Of>, Parsed<Of>>

/** Statement containing [Parsed]s */
typealias ParsedStatement = AST.Statement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** Block containing [Parsed]s */
typealias ParsedBlock = AST.Block<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** IfStatement containing [Parsed]s */
typealias ParsedIfStatement = AST.IfStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** WhileStatement containing [Parsed]s */
typealias ParsedWhileStatement = AST.WhileStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
/** ReturnStatement containing [Parsed]s */
typealias ParsedReturnStatement = AST.ReturnStatement<Parsed<Of>, Parsed<Of>>
/** ExpressionStatement containing [Parsed]s */
typealias ParsedExpressionStatement = AST.ExpressionStatement<Parsed<Of>, Parsed<Of>>

/** Expression containing [Parsed]s */
typealias ParsedExpression = AST.Expression<Parsed<Of>, Parsed<Of>>
/** BinaryExpression containing [Parsed]s */
typealias ParsedBinaryExpression = AST.BinaryExpression<Parsed<Of>, Parsed<Of>>
/** UnaryExpression containing [Parsed]s */
typealias ParsedUnaryExpression = AST.UnaryExpression<Parsed<Of>, Parsed<Of>>
/** MethodInvocationExpression containing [Parsed]s */
typealias ParsedMethodInvocationExpression = AST.MethodInvocationExpression<Parsed<Of>, Parsed<Of>>
/** FieldAccessExpression containing [Parsed]s */
typealias ParsedFieldAccessExpression = AST.FieldAccessExpression<Parsed<Of>, Parsed<Of>>
/** ArrayAccessExpression containing [Parsed]s */
typealias ParsedArrayAccessExpression = AST.ArrayAccessExpression<Parsed<Of>, Parsed<Of>>
/** IdentifierExpression containing [Parsed]s */
typealias ParsedIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [Parsed]s */
typealias ParsedLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [Parsed]s */
typealias ParsedNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [Parsed]s */
typealias ParsedNewArrayExpression = AST.NewArrayExpression<Parsed<Of>, Parsed<Of>>


/************************************************
 ** Typed
 ************************************************/
/** Type containing [SynthesizedTyped]s */
typealias TypedType = Type<SynthesizedTyped<Of>>
/** Array containing [SynthesizedTyped]s */
typealias TypedArray = Array<SynthesizedTyped<Of>>
/** ArrayType containing [SynthesizedTyped]s */
typealias TypedArrayType = Type.Array.ArrayType<SynthesizedTyped<Of>>

/** Program containing [SynthesizedTyped]s */
typealias TypedProgram = AST.Program<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** ClassDeclaration containing [SynthesizedTyped]s */
typealias TypedClassDeclaration = AST.ClassDeclaration<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>

/** ClassMember containing [SynthesizedTyped]s */
typealias TypedClassMember = AST.ClassMember<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** Field containing [SynthesizedTyped]s */
typealias TypedField = AST.Field<SynthesizedTyped<Of>>
/** Method containing [SynthesizedTyped]s */
typealias TypedMethod = AST.Method<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** MainMethod containing [SynthesizedTyped]s */
typealias TypedMainMethod = AST.MainMethod<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** Parameter containing [SynthesizedTyped]s */
typealias TypedParameter = AST.Parameter<SynthesizedTyped<Of>>

/** BlockStatement containing [SynthesizedTyped]s */
typealias TypedBlockStatement = AST.BlockStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** LocalVariableDeclaration containing [SynthesizedTyped]s */
typealias TypedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** StmtWrapper containing [SynthesizedTyped]s */
typealias TypedStmtWrapper = AST.StmtWrapper<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>

/** Statement containing [SynthesizedTyped]s */
typealias TypedStatement = AST.Statement<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** Block containing [SynthesizedTyped]s */
typealias TypedBlock = AST.Block<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** IfStatement containing [SynthesizedTyped]s */
typealias TypedIfStatement = AST.IfStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** WhileStatement containing [SynthesizedTyped]s */
typealias TypedWhileStatement = AST.WhileStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** ReturnStatement containing [SynthesizedTyped]s */
typealias TypedReturnStatement = AST.ReturnStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** ExpressionStatement containing [SynthesizedTyped]s */
typealias TypedExpressionStatement = AST.ExpressionStatement<SynthesizedTyped<Of>, SynthesizedTyped<Of>>

/** Expression containing [SynthesizedTyped]s */
typealias TypedExpression = AST.Expression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** BinaryExpression containing [SynthesizedTyped]s */
typealias TypedBinaryExpression = AST.BinaryExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** UnaryExpression containing [SynthesizedTyped]s */
typealias TypedUnaryExpression = AST.UnaryExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** MethodInvocationExpression containing [SynthesizedTyped]s */
typealias TypedMethodInvocationExpression = AST.MethodInvocationExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** FieldAccessExpression containing [SynthesizedTyped]s */
typealias TypedFieldAccessExpression = AST.FieldAccessExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** ArrayAccessExpression containing [SynthesizedTyped]s */
typealias TypedArrayAccessExpression = AST.ArrayAccessExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>
/** IdentifierExpression containing [SynthesizedTyped]s */
typealias TypedIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [SynthesizedTyped]s */
typealias TypedLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [SynthesizedTyped]s */
typealias TypedNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [SynthesizedTyped]s */
typealias TypedNewArrayExpression = AST.NewArrayExpression<SynthesizedTyped<Of>, SynthesizedTyped<Of>>


/************************************************
 ** LenientTyped
 ************************************************/
/** Type containing [LenientTyped]s */
typealias LenientTypedType = Type<LenientTyped<Of>>
/** Array containing [LenientTyped]s */
typealias LenientTypedArray = Array<LenientTyped<Of>>
/** ArrayType containing [LenientTyped]s */
typealias LenientTypedArrayType = Type.Array.ArrayType<LenientTyped<Of>>

/** Program containing [LenientTyped]s */
typealias LenientTypedProgram = AST.Program<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** ClassDeclaration containing [LenientTyped]s */
typealias LenientTypedClassDeclaration = AST.ClassDeclaration<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>

/** ClassMember containing [LenientTyped]s */
typealias LenientTypedClassMember = AST.ClassMember<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** Field containing [LenientTyped]s */
typealias LenientTypedField = AST.Field<LenientTyped<Of>>
/** Method containing [LenientTyped]s */
typealias LenientTypedMethod = AST.Method<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** MainMethod containing [LenientTyped]s */
typealias LenientTypedMainMethod = AST.MainMethod<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** Parameter containing [LenientTyped]s */
typealias LenientTypedParameter = AST.Parameter<LenientTyped<Of>>

/** BlockStatement containing [LenientTyped]s */
typealias LenientTypedBlockStatement = AST.BlockStatement<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** LocalVariableDeclaration containing [LenientTyped]s */
typealias LenientTypedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<LenientTyped<Of>, LenientTyped<Of>>
/** StmtWrapper containing [LenientTyped]s */
typealias LenientTypedStmtWrapper = AST.StmtWrapper<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>

/** Statement containing [LenientTyped]s */
typealias LenientTypedStatement = AST.Statement<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** Block containing [LenientTyped]s */
typealias LenientTypedBlock = AST.Block<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** IfStatement containing [LenientTyped]s */
typealias LenientTypedIfStatement = AST.IfStatement<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** WhileStatement containing [LenientTyped]s */
typealias LenientTypedWhileStatement = AST.WhileStatement<LenientTyped<Of>, LenientTyped<Of>, LenientTyped<Of>>
/** ReturnStatement containing [LenientTyped]s */
typealias LenientTypedReturnStatement = AST.ReturnStatement<LenientTyped<Of>, LenientTyped<Of>>
/** ExpressionStatement containing [LenientTyped]s */
typealias LenientTypedExpressionStatement = AST.ExpressionStatement<LenientTyped<Of>, LenientTyped<Of>>

/** Expression containing [LenientTyped]s */
typealias LenientTypedExpression = AST.Expression<LenientTyped<Of>, LenientTyped<Of>>
/** BinaryExpression containing [LenientTyped]s */
typealias LenientTypedBinaryExpression = AST.BinaryExpression<LenientTyped<Of>, LenientTyped<Of>>
/** UnaryExpression containing [LenientTyped]s */
typealias LenientTypedUnaryExpression = AST.UnaryExpression<LenientTyped<Of>, LenientTyped<Of>>
/** MethodInvocationExpression containing [LenientTyped]s */
typealias LenientTypedMethodInvocationExpression = AST.MethodInvocationExpression<LenientTyped<Of>, LenientTyped<Of>>
/** FieldAccessExpression containing [LenientTyped]s */
typealias LenientTypedFieldAccessExpression = AST.FieldAccessExpression<LenientTyped<Of>, LenientTyped<Of>>
/** ArrayAccessExpression containing [LenientTyped]s */
typealias LenientTypedArrayAccessExpression = AST.ArrayAccessExpression<LenientTyped<Of>, LenientTyped<Of>>
/** IdentifierExpression containing [LenientTyped]s */
typealias LenientTypedIdentifierExpression = AST.IdentifierExpression
/** LiteralExpression containing [LenientTyped]s */
typealias LenientTypedLiteralExpression = AST.LiteralExpression
/** NewObjectExpression containing [LenientTyped]s */
typealias LenientTypedNewObjectExpression = AST.NewObjectExpression
/** NewArrayExpression containing [LenientTyped]s */
typealias LenientTypedNewArrayExpression = AST.NewArrayExpression<LenientTyped<Of>, LenientTyped<Of>>
