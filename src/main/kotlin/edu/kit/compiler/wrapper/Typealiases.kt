package edu.kit.compiler.wrapper

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type

/**
 * This is just a file full of aliases for different wrapper applications, that we might use
 * Not all the aliases are used, but having the complete list makes it easy to add new aliases, just
 * by copy and pasting the whole block and replacing the wrapper name
 */

/************************************************
 ** Lenient
 ************************************************/
typealias LenientType = Type<Lenient<Of>>
typealias LenientArray = Array<Lenient<Of>>
typealias LenientArrayType = Type.Array.ArrayType<Lenient<Of>>

typealias LenientProgram = AST.Program<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientClassDeclaration = AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>

typealias LenientClassMember = AST.ClassMember<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientField = AST.Field<Lenient<Of>>
typealias LenientMethod = AST.Method<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientMainMethod = AST.MainMethod<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientParameter = AST.Parameter<Lenient<Of>>

typealias LenientBlockStatement = AST.BlockStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Lenient<Of>, Lenient<Of>>
typealias LenientStmtWrapper = AST.StmtWrapper<Lenient<Of>, Lenient<Of>, Lenient<Of>>

typealias LenientStatement = AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientBlock = AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientIfStatement = AST.IfStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientWhileStatement = AST.WhileStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias LenientReturnStatement = AST.ReturnStatement<Lenient<Of>, Lenient<Of>>
typealias LenientExpressionStatement = AST.ExpressionStatement<Lenient<Of>, Lenient<Of>>

typealias LenientExpression = AST.Expression<Lenient<Of>, Lenient<Of>>
typealias LenientBinaryExpression = AST.BinaryExpression<Lenient<Of>, Lenient<Of>>
typealias LenientUnaryExpression = AST.UnaryExpression<Lenient<Of>, Lenient<Of>>
typealias LenientMethodInvocationExpression = AST.MethodInvocationExpression<Lenient<Of>, Lenient<Of>>
typealias LenientFieldAccessExpression = AST.FieldAccessExpression<Lenient<Of>, Lenient<Of>>
typealias LenientArrayAccessExpression = AST.ArrayAccessExpression<Lenient<Of>, Lenient<Of>>
typealias LenientIdentifierExpression = AST.IdentifierExpression
typealias LenientLiteralExpression<T> = AST.LiteralExpression<T>
typealias LenientNewObjectExpression = AST.NewObjectExpression
typealias LenientNewArrayExpression = AST.NewArrayExpression<Lenient<Of>, Lenient<Of>>

/************************************************
 ** Identity
 ************************************************/
typealias IdentityType = Type<Identity<Of>>
typealias IdentityArray = Array<Identity<Of>>
typealias IdentityArrayType = Type.Array.ArrayType<Identity<Of>>

typealias IdentityProgram = AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityClassDeclaration = AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>

typealias IdentityClassMember = AST.ClassMember<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityField = AST.Field<Identity<Of>>
typealias IdentityMethod = AST.Method<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityMainMethod = AST.MainMethod<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityParameter = AST.Parameter<Identity<Of>>

typealias IdentityBlockStatement = AST.BlockStatement<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>
typealias IdentityStmtWrapper = AST.StmtWrapper<Identity<Of>, Identity<Of>, Identity<Of>>

typealias IdentityStatement = AST.Statement<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityBlock = AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityIfStatement = AST.IfStatement<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityWhileStatement = AST.WhileStatement<Identity<Of>, Identity<Of>, Identity<Of>>
typealias IdentityReturnStatement = AST.ReturnStatement<Identity<Of>, Identity<Of>>
typealias IdentityExpressionStatement = AST.ExpressionStatement<Identity<Of>, Identity<Of>>

typealias IdentityExpression = AST.Expression<Identity<Of>, Identity<Of>>
typealias IdentityBinaryExpression = AST.BinaryExpression<Identity<Of>, Identity<Of>>
typealias IdentityUnaryExpression = AST.UnaryExpression<Identity<Of>, Identity<Of>>
typealias IdentityMethodInvocationExpression = AST.MethodInvocationExpression<Identity<Of>, Identity<Of>>
typealias IdentityFieldAccessExpression = AST.FieldAccessExpression<Identity<Of>, Identity<Of>>
typealias IdentityArrayAccessExpression = AST.ArrayAccessExpression<Identity<Of>, Identity<Of>>
typealias IdentityIdentifierExpression = AST.IdentifierExpression
typealias IdentityLiteralExpression<T> = AST.LiteralExpression<T>
typealias IdentityNewObjectExpression = AST.NewObjectExpression
typealias IdentityNewArrayExpression = AST.NewArrayExpression<Identity<Of>, Identity<Of>>

/************************************************
 ** Positioned
 ************************************************/
typealias PositionedType = Type<Positioned<Of>>
typealias PositionedArray = Array<Positioned<Of>>
typealias PositionedArrayType = Type.Array.ArrayType<Positioned<Of>>

typealias PositionedProgram = AST.Program<Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedClassDeclaration = AST.ClassDeclaration<Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>

typealias PositionedClassMember = AST.ClassMember<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedField = AST.Field<Positioned<Of>>
typealias PositionedMethod = AST.Method<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedMainMethod = AST.MainMethod<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedParameter = AST.Parameter<Positioned<Of>>

typealias PositionedBlockStatement = AST.BlockStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Positioned<Of>, Positioned<Of>>
typealias PositionedStmtWrapper = AST.StmtWrapper<Positioned<Of>, Positioned<Of>, Positioned<Of>>

typealias PositionedStatement = AST.Statement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedBlock = AST.Block<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedIfStatement = AST.IfStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedWhileStatement = AST.WhileStatement<Positioned<Of>, Positioned<Of>, Positioned<Of>>
typealias PositionedReturnStatement = AST.ReturnStatement<Positioned<Of>, Positioned<Of>>
typealias PositionedExpressionStatement = AST.ExpressionStatement<Positioned<Of>, Positioned<Of>>

typealias PositionedExpression = AST.Expression<Positioned<Of>, Positioned<Of>>
typealias PositionedBinaryExpression = AST.BinaryExpression<Positioned<Of>, Positioned<Of>>
typealias PositionedUnaryExpression = AST.UnaryExpression<Positioned<Of>, Positioned<Of>>
typealias PositionedMethodInvocationExpression = AST.MethodInvocationExpression<Positioned<Of>, Positioned<Of>>
typealias PositionedFieldAccessExpression = AST.FieldAccessExpression<Positioned<Of>, Positioned<Of>>
typealias PositionedArrayAccessExpression = AST.ArrayAccessExpression<Positioned<Of>, Positioned<Of>>
typealias PositionedIdentifierExpression = AST.IdentifierExpression
typealias PositionedLiteralExpression<T> = AST.LiteralExpression<T>
typealias PositionedNewObjectExpression = AST.NewObjectExpression
typealias PositionedNewArrayExpression = AST.NewArrayExpression<Positioned<Of>, Positioned<Of>>

/************************************************
 ** Parsed
 ************************************************/
typealias ParsedType = Type<Parsed<Of>>
typealias ParsedArray = Array<Parsed<Of>>
typealias ParsedArrayType = Type.Array.ArrayType<Parsed<Of>>

typealias ParsedProgram = AST.Program<Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedClassDeclaration = AST.ClassDeclaration<Parsed<Of>, Parsed<Of>, Parsed<Of>, Parsed<Of>>

typealias ParsedClassMember = AST.ClassMember<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedField = AST.Field<Parsed<Of>>
typealias ParsedMethod = AST.Method<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedMainMethod = AST.MainMethod<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedParameter = AST.Parameter<Parsed<Of>>

typealias ParsedBlockStatement = AST.BlockStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedLocalVariableDeclaration = AST.LocalVariableDeclarationStatement<Parsed<Of>, Parsed<Of>>
typealias ParsedStmtWrapper = AST.StmtWrapper<Parsed<Of>, Parsed<Of>, Parsed<Of>>

typealias ParsedStatement = AST.Statement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedBlock = AST.Block<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedIfStatement = AST.IfStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedWhileStatement = AST.WhileStatement<Parsed<Of>, Parsed<Of>, Parsed<Of>>
typealias ParsedReturnStatement = AST.ReturnStatement<Parsed<Of>, Parsed<Of>>
typealias ParsedExpressionStatement = AST.ExpressionStatement<Parsed<Of>, Parsed<Of>>

typealias ParsedExpression = AST.Expression<Parsed<Of>, Parsed<Of>>
typealias ParsedBinaryExpression = AST.BinaryExpression<Parsed<Of>, Parsed<Of>>
typealias ParsedUnaryExpression = AST.UnaryExpression<Parsed<Of>, Parsed<Of>>
typealias ParsedMethodInvocationExpression = AST.MethodInvocationExpression<Parsed<Of>, Parsed<Of>>
typealias ParsedFieldAccessExpression = AST.FieldAccessExpression<Parsed<Of>, Parsed<Of>>
typealias ParsedArrayAccessExpression = AST.ArrayAccessExpression<Parsed<Of>, Parsed<Of>>
typealias ParsedIdentifierExpression = AST.IdentifierExpression
typealias ParsedLiteralExpression<T> = AST.LiteralExpression<T>
typealias ParsedNewObjectExpression = AST.NewObjectExpression
typealias ParsedNewArrayExpression = AST.NewArrayExpression<Parsed<Of>, Parsed<Of>>
