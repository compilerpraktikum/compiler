# MiniJava 4.0 - Syntax
*View this file in vscode or some markdown viewer capable of inline Latex!*
## Base Grammar

*Änderungen zur Grammatik im Sprachbericht sind mit grün und rot markiert.*

$ $                                 | $ $   | $ $
---:                                | :---: |:---
$Program$                           | $\to$ | $ClassDeclaration^ \ast$
$ClassDeclaration$                  | $\to$ | **`class IDENT {`** $ClassMember^ \ast$ **`}`**
$ClassMember$                       | $\to$ | **`public`** $MethodPrefix$
<span style="color:red">--</span>$Field$ | $\to$ | **`public`** $Type$ **`IDENT`** **`;`**
<span style="color:red">--</span>$Method$ | $\to$ | **`public`** $Type$ **`IDENT (`** $Parameters?$ **`)`** $MethodRest ?$ $Block$
<span style="color:green">++</span>$MethodPrefix$ | $\to$ | $FieldMethodPrefix$ $\|$ $MainMethod$
<span style="color:green">++</span>$FieldMethodPrefix$ | $\to$ | $Type$ **`IDENT`** $FieldMethodRest$
<span style="color:green">++</span>$FieldMethodRest$ | $\to$ | $Field$ $\|$ $Method$
<span style="color:green">++</span>$Field$ | $\to$ | **`;`**
<span style="color:green">++</span>$Method$ | $\to$ | **`(`** $Parameters?$ **`)`** $MethodRest ?$ $Block$
<span style="color:red">--</span>$MainMethod$ | $\to$ | **`public static void IDENT (`** $Type$ **`IDENT )`** $MethodRest ?$ $Block$
<span style="color:green">++</span>$MainMethod$ | $\to$ | **`static void IDENT (`** $Type$ **`IDENT )`** $MethodRest ?$ $Block$
$MethodRest$                        | $\to$ | **`throws IDENT`**
<span style="color:red">--</span>$Parameters$ | $\to$ | $Parameter$ $\|$ $Parameter$ **`,`** $Parameters$
<span style="color:green">++</span>$Parameters$ | $\to$ | $Parameter$ $($ **`,`** $Parameter$ $)*$
$Parameter$                         | $\to$ | $Type$ **`IDENT`**
<span style="color:red">--</span>$Type$ | $\to$ | $Type$ **`[ ]`** $\|$ $BasicType$
<span style="color:green">++</span>$Type$ | $\to$ | $BasicType$ $($ **`[ ]`** $BasicType$ $)*$
$BasicType$                         | $\to$ | **`int`** $\|$ **`boolean`** $\|$ **`void`** $\|$ **`IDENT`** $\|$ 
$ $                                 | $ $   | $ $
$Statement$                         | $\to$ | $Block$ <br/>$\|$ $EmptyStatement$ <br/>$\|$ $IfStatement$ <br/>$\|$ $ExpressionStatement$ <br/>$\|$ $WhileStatement$ <br/>$\|$ $ReturnStatement$
$Block$                             | $\to$ | **`{`** $BlockStatement^ \ast$ **`}`**
$BlockStatement$                    | $\to$ | $Statement$ $\|$ $LocalVariableDeclarationStatement$ $\|$
$LocalVariableDeclarationStatement$ | $\to$ | $Type$ **`IDENT`** $($ **`=`** $Expression )?$ **`;`**
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
$PrimaryExpression$                 | $\to$ | **`null`** <br/>$\|$ **`false`** <br/>$\|$ **`true`** <br/>$\|$ **`INTEGER_LITERAL`** <br/>$\|$ **`IDENT`** <br/>$\|$ **`IDENT (`** $Arguments$ **`)`** <br/>$\|$  **`this`** <br/>$\|$  **`(`** $Expression$ **`)`** <br/>$\|$ <span style="color:red">--</span> $NewObjectExpression$ <br/>$\|$ <span style="color:red">--</span> $NewArrayExpression$ <br/>$\|$ <span style="color:green">++</span> **`new`** $NewObjectArrayExpression$
<span style="color:red">--</span>$NewObjectExpression$ | $\to$ | **`new IDENT ( )`**
<span style="color:red">--</span>$NewArrayExpression$ | $\to$ | **`new`** $BasicType$ **`[`** $Expression$ **`]`** $($ **`[ ]`** $)^ \ast$
<span style="color:green">++</span>$NewObjectArrayExpression$ | $\to$ | $NewObjectExpression$ $\|$ $NewArrayExpression$
<span style="color:green">++</span>$NewObjectExpression$ | $\to$ | **`IDENT ( )`**
<span style="color:green">++</span>$NewArrayExpression$ | $\to$ | $BasicType$ **`[`** $Expression$ **`]`** $($ **`[ ]`** $)^ \ast$


*Vermutung: Wegen NewObjectArrayExpression ist das ganze SLL(2) (beide fangen mit **`IDENT`** an)*

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
