# MiniJava 4.0 - Syntax

## Base Grammar

$ $                                 | $ $   | $ $
---:                                | :---: |:---
$Program$                           | $\to$ | $ClassDeclaration^ \ast$
$ClassDeclaration$                  | $\to$ | **`class IDENT {`** $ClassMember^ \ast$ **`}`**
$ClassMember$                       | $\to$ | $Field$ $\|$ $Method$ $\|$ $MainMethod$
$Field$                             | $\to$ | **`public`** $Type$ **`IDENT`** **`;`**
$MainMethod$                        | $\to$ | **`public static void IDENT (`** $Type$ **`IDENT )`** $MethodRest ?$ $Block$
$Method$                            | $\to$ | **`public`** $Type$ **`IDENT (`** $Parameters?$ **`)`** $MethodRest ?$ $Block$
$MethodRest$                        | $\to$ | **`throws IDENT`**
$Parameters$                        | $\to$ | $Parameter$ $\|$ $Parameter$ **`,`** $Parameters$
$Parameter$                         | $\to$ | $Type$ **`IDENT`**
$Type$                              | $\to$ | $Type$ **`[ ]`** $\|$ $BasicType$
$BasicType$                         | $\to$ | **`int`** $\|$ **`boolean`** $\|$ **`void`** $\|$ **`IDENT`** $\|$ 
$ $                                 | $ $   | $ $
$Statement$                         | $\to$ | $Block$ <br/>$\|$ $EmptyStatement$ <br/>$\|$ $IfStatement$ <br/>$\|$ $ExpressionStatement$ <br/>$\|$ $WhileStatement$ <br/>$\|$ $ReturnStatement$
$Block$                             | $\to$ | **`{`** $BlockStatement^ \ast$ **`}`**
$BlockStatement$                    | $\to$ | $Statement$ $\|$ $LocalVariableDeclarationStatement$ $\|$
$LocalVariableDeclarationStatement$ | $\to$ | $Expression$ **`;`**
$EmptyStatement$                    | $\to$ | **`;`**
$WhileStatement$                    | $\to$ | **`while (`** $Expression$ **`)`** $Statement$
$IfStatement$                       | $\to$ | **`if (`** $Expression$ **`)`** $Statement$ $($ **`else`** $Statement$ $)$ 
$ExpressionStatement$               | $\to$ | $Expression$ **`;`**
$ReturnStatement$                   | $\to$ | **`return`** $Expression ?$ **`;`**
$ $                                 | $ $   | $ $
$Expression$                        | $\to$ | <span style="color:green">*See Precedence Table*</span>
$\dots$                             | $\dots$   | $\dots$
$ $                                 | $ $   | $ $
$MethodInvocation$                  | $\to$ | **`. IDENT (`** $Arguments$ **`)`**
$FieldAccess$                       | $\to$ | **`. IDENT`**
$ArrayAccess$                       | $\to$ | **`[`** $Expression$ **`]`**
$Arguments$                         | $\to$ | $( Expression$ $($ **`,`** $Expression)^ \ast)?$
$PrimaryExpression$                 | $\to$ | **`null`** <br/>$\|$ **`false`** <br/>$\|$ **`true`** <br/>$\|$ **`INTEGER_LITERAL`** <br/>$\|$ **`IDENT`** <br/>$\|$ **`IDENT (`** $Arguments$ **`)`** <br/>$\|$  **`this`** <br/>$\|$  **`(`** $Expression$ **`)`** <br/>$\|$ $NewObjectExpression$ <br/>$\|$ $NewArrayExpression$
$NewObjectExpression$               | $\to$ | **`new IDENT ( )`**
$NewArrayExpression$                | $\to$ | **`new`** $BasicType$ **`[`** $Expression$ **`]`** $($ **`[ ]`** $)^ \ast$


## Operator Precedences

**Name**                            | **Token(s)**                                      | *Precedence* | *Associativity*
:---                                |:---                                               | :---         | :---
$Assignment$                        | **`=`**                                           | $1$          | Right to Left
$LogicalOr$                         | **`\|\|`**                                        | $2$          | Left to Right
$LogicalAnd$                        | **`&&`**                                          | $3$          | Left to Right
$Equality$                          | **`==`** $\|$ **`!=`**                            | $4$          | Left to Right
$Relational$                        | **`<`** $\|$ **`<=`** $\|$ **`>`** $\|$ **`>=`**  | $5$          | Left to Right
$Additive$                          | **`+`** $\|$ **`-`**                              | $6$          | Left to Right
$Multiplicative$                    | **`*`** $\|$ **`/`** $\|$ **`%`**               | $7$          | Left to Right
$Unary$                             | **`!`** $\|$ **`-`**                            | $8$          | Right to Left
$Postfix$                           | **`.`** $\|$ **`[ ]`**                          | $9$          | Left to Right
