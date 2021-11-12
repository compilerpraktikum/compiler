# MiniJava 4.0 - Attributierte Grammatik

$ $                                 | $ $   | $  $                                                                                                                                              | $ $ | $asd$
---:                                | :---: | :---                                                                                                                                              | :-- | :--
$Program$                           | $\to$ | $ClassDeclaration^ \ast$                                                                                                                          | $ $ |
$ClassDeclaration$                  | $\to$ | **`class IDENT {`** $ClassMember^ \ast$ **`}`**                                                                                                   | $ $ |
$ClassMember$                       | $\to$ | **`public`** $MethodPrefix$                                                                                                                       | $ $ |
$MethodPrefix$                      | $\to$ | $FieldMethodPrefix$ $\|$ $MainMethod$                                                                                                             | $ $ |
$FieldMethodPrefix$                 | $\to$ | $Type$ **`IDENT`** $FieldMethodRest$                                                                                                              | $ $ |
$FieldMethodRest$                   | $\to$ | $Field$ $\|$ $Method$                                                                                                                             | $ $ |
$Field$                             | $\to$ | **`;`**                                                                                                                                           | $ $ |
$Method$                            | $\to$ | **`(`** $Parameters?$ **`)`** $MethodRest ?$ $Block$                                                                                              | $ $ |a
$MainMethod$                        | $\to$ | **`static void IDENT (`** $Type$ **`IDENT )`** $MethodRest ?$ $Block$                                                                             | $ $ |
$MethodRest$                        | $\to$ | **`throws IDENT`**                                                                                                                                | $ $ |
$Parameters$                        | $\to$ | $Parameter$ $($ **`,`** $Parameter$ $)*$                                                                                                          | $ $ |
$Parameter$                         | $\to$ | $Type$ **`IDENT`**                                                                                                                                | $ $ |
$Type$                              | $\to$ | $BasicType$ $($ **`[ ]`** $)*$                                                                                                                    | $ $ |a
$BasicType$                         | $\to$ | **`int`** $\|$ **`boolean`** $\|$ **`void`** $\|$ **`IDENT`**                                                                                     | $ $ |
$ $                                 | $ $   | $ $                                                                                                                                               | $ $ |a
$Statement$                         | $\to$ | $Block$ <br/>$\|$ $EmptyStatement$ <br/>$\|$ $IfStatement$ <br/>$\|$ $ExpressionStatement$ <br/>$\|$ $WhileStatement$ <br/>$\|$ $ReturnStatement$ | $ $ |
$Block$                             | $\to$ | **`{`** $BlockStatement^ \ast$ **`}`**                                                                                                            | $ $ |aa
$BlockStatement$                    | $\to$ | $Statement$ $\|$ $LocalVariableDeclarationStatement$ $\|$                                                                                         | $ $ |
$LocalVariableDeclarationStatement$ | $\to$ | $Type$ **`IDENT`** $($ **`=`** $Expression )?$ **`;`**                                                                                            | $ $ |
$EmptyStatement$                    | $\to$ | **`;`**                                                                                                                                           | $ $ |
$WhileStatement$                    | $\to$ | **`while (`** $Expression$ **`)`** $Statement$                                                                                                    | $ $ |
$IfStatement$                       | $\to$ | **`if (`** $Expression$ **`)`** $Statement$ $($ **`else`** $Statement$ $)?$                                                                       | $ $ |
$ExpressionStatement$               | $\to$ | $Expression$ **`;`**                                                                                                                              | $ $ |
$ReturnStatement$                   | $\to$ | **`return`** $Expression ?$ **`;`**                                                                                                               | $ $ |a
$ $                                 | $ $   | $ $                                                                                                                                               | $ $ |
$Expression$                        | $\to$ | <span style="color:green">*See Precedence Table*</span>                                                                                           | $ $ |
$\dots$                             | $\dots$   | $\dots$                                                                                                                                       | $ $ |
$UnaryExpression$                   | $\to$ | $PostfixExpression$ $\|$ $($ **`!`** $\|$ **`-`** $)$  $UnaryExpression$                                                                          | $ $ |
$PostfixExpression$                 | $\to$ | $PrimaryExpression$ $(PostfixOp)^*$                                                                                                               | $ $ |
$PostfixOp$ | $\to$ | $MethodInvocationFieldAccess$ <br/>$\|$ $ArrayAccess$                                                                                                                     | $ $ |
$MethodInvocationFieldAccess$ | $\to$ | **`. IDENT`**                                                                                                                                           | $ $ |
$ $                                 | $ $   | $ $                                                                                                                                               | $ $ |
$MethodInvocation$                  | $\to$ | **`. IDENT (`** $Arguments$ **`)`**                                                                                                               | $ $ |
$FieldAccess$                       | $\to$ | **`. IDENT`**                                                                                                                                     | $ $ |
$ArrayAccess$                       | $\to$ | **`[`** $Expression$ **`]`**                                                                                                                      | $ $ |
$Arguments$                         | $\to$ | $( Expression$ $($ **`,`** $Expression)^ \ast)?$                                                                                                  | $ $ |
$PrimaryExpression$                 | $\to$ | **`null`** <br/>$\|$ **`false`** <br/>$\|$ **`true`** <br/>$\|$ **`INTEGER_LITERAL`** <br/>$\|$ **`IDENT`** $($ **`(`** $Arguments$ **`)`**$)?$ <br/>$\|$  **`this`** <br/>$\|$  **`(`** $Expression$ **`)`** <br/>$\|$ **`new`** $NewObjectArrayExpression$\ast$                                                                                                                                                                 | $ $ |
$NewObjectArrayExpression$          | $\to$ | $NewObjectExpression$ $\|$ $NewArrayExpression$                                                                                                   | $ $ |
$NewObjectExpression$               | $\to$ | **`IDENT ( )`**                                                                                                                                   | $ $ |
$NewArrayExpression$                | $\to$ | $BasicType$ **`[`** $Expression$ **`]`** $($ **`[ ]`** $)^ \ast$                                                                                  | $ $ |
