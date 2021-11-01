# MiniJava 4.0 - Syntax

## Base Grammar

$ $                                 | $ $   | $ $
---:                                | :---: |:---
$Program$                           | $\to$ | $ClassDeclaration^ \ast$
$ClassDeclaration$                  | $\to$ | **`class IDENT {`** $ClassMember^ \ast$ **`}`**
$ClassMember$                       | $\to$ | $Field$ $\|$ $Method$ $\|$ $MainMethod$
$Field$                             | $\to$ | $todo$
$MainMethod$                        | $\to$ | $todo$
$Method$                            | $\to$ | $todo$
$MethodRest$                        | $\to$ | $todo$
$Parameters$                        | $\to$ | $todo$
$Parameter$                         | $\to$ | $todo$
$Type$                              | $\to$ | $todo$
$BasicType$                         | $\to$ | $todo$
$ $                                 | $ $   | $ $
$Statement$                         | $\to$ | $Block$ <br/>$\|$ $EmptyStatement$ <br/>$\|$ $IfStatement$ <br/>$\|$ $ExpressionStatement$ <br/>$\|$ $WhileStatement$ <br/>$\|$ $ReturnStatement$
$Block$                             | $\to$ | **`{`** $BlockStatement^ \ast$ **`}`**
$BlockStatement$                    | $\to$ | $Statement$ $\|$ $LocalVariableDeclarationStatement$ $\|$
$LocalVariableDeclarationStatement$ | $\to$ | $todo$
$EmptyStatement$                    | $\to$ | $todo$
$WhileStatement$                    | $\to$ | **`while (`** $Expression$ **`)`** $Statement$
$IfStatement$                       | $\to$ | **`if (`** $Expression$ **`)`** $Statement$ $($ **`else`** $Statement$ $)$ 
$ExpressionStatement$               | $\to$ | $Expression$ **`;`**
$ReturnStatement$                   | $\to$ | **`return`** $Expression ?$ **`;`**
$ $                                 | $ $   | $ $
$Expression$                        | $\to$ | <span style="color:green">*See Precedence Table*</span>
$\dots$                             | $\dots$   | $\dots$
$ $                                 | $ $   | $ $
$MethodInvocation$                  | $\to$ | $todo$
$FieldAccess$                       | $\to$ | $todo$
$ArrayAccess$                       | $\to$ | $todo$
$Arguments$                         | $\to$ | $todo$
$PrimaryExpression$                 | $\to$ | $todo$
$NewObjectExpression$               | $\to$ | $todo$


## Operator Precedences

**Name**                            | **Token(s)**                                      | *Precedence* | *Associativity*
:---                                |:---                                               | :---         | :---
$Assignment$                        | **`=`**                                           | $1$          | todo
$LogicalOr$                         | **`\|\|`**                                        | $2$          | todo
$LogicalAnd$                        | **`&&`**                                          | $3$          | todo
$Equality$                          | **`==`** $\|$ **`!=`**                            | $4$          | todo
$Relational$                        | **`<`** $\|$ **`<=`** $\|$ **`>`** $\|$ **`>=`**  | $5$          | todo
$Additive$                          | **`+`** $\|$ **`-`**                              | $6$          | todo
$Multiplicative$                    | **`*`** $\|$ **`/`** $\|$ **`%`**               | $7$          | todo
$Unary$                             | **`!`** $\|$ **`-`**                            | $8$          | Right to Left
$Postfix$                           | **`.`** $\|$ **`[-]`**                          | $8$          | todo
