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
<span style="color:green">++</span>$Type$ | $\to$ | $BasicType$ $($ **`[ ]`** $)*$
$BasicType$                         | $\to$ | **`int`** $\|$ **`boolean`** $\|$ **`void`** $\|$ **`IDENT`**
$ $                                 | $ $   | $ $
$Statement$                         | $\to$ | $Block$ <br/>$\|$ $EmptyStatement$ <br/>$\|$ $IfStatement$ <br/>$\|$ $ExpressionStatement$ <br/>$\|$ $WhileStatement$ <br/>$\|$ $ReturnStatement$
$Block$                             | $\to$ | **`{`** $BlockStatement^ \ast$ **`}`**
$BlockStatement$                    | $\to$ | $Statement$ $\|$ $LocalVariableDeclarationStatement$ $\|$
$LocalVariableDeclarationStatement$ | $\to$ | $Type$ **`IDENT`** $($ **`=`** $Expression )?$ **`;`**
$EmptyStatement$                    | $\to$ | **`;`**
$WhileStatement$                    | $\to$ | **`while (`** $Expression$ **`)`** $Statement$
$IfStatement$                       | $\to$ | **`if (`** $Expression$ **`)`** $Statement$ $($ **`else`** $Statement$ $)?    $ 
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
$PrimaryExpression$                 | $\to$ | **`null`** <br/>$\|$ **`false`** <br/>$\|$ **`true`** <br/>$\|$ **`INTEGER_LITERAL`** <br/>$\|$ <span style="color:red">--</span> **`IDENT`** <br/>$\|$ <span style="color:red">--</span> **`IDENT (`** $Arguments$ **`)`**<br/>$\|$ <span style="color:green">++</span> **`IDENT`** $($ **`(`** $Arguments$ **`)`**$)?$ <br/>$\|$  **`this`** <br/>$\|$  **`(`** $Expression$ **`)`** <br/>$\|$ <span style="color:red">--</span> $NewObjectExpression$ <br/>$\|$ <span style="color:red">--</span> $NewArrayExpression$ <br/>$\|$ <span style="color:green">++</span> **`new`** $NewObjectArrayExpression$
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

## First & Follow Sets

### $First_2$-Sets

$ $                                 | $ $   | $ $
---:                                | :---: |:---
$First_2(Programm)$ | $=$ | $First_2(ClassDeclaration)$*
$First_2(ClassDeclaration)$ | $=$ | $\{$ **`class IDENT`** $\}$
$First_2(ClassMember)$ | $=$ | $\{$ **`public`** $\} \times_2 First_2(MethodPrefix)$ 
$First_2(MethodPrefix)$ | $=$ | $First_2(FieldMethodPrefix) \cup First_2(MainMethod)$ 
$First_2(FieldMethodPrefix)$ | $=$ | $First_2(Type) \times_2 \{$ **`IDENT`** $\}$
$First_2(FieldMethodRest)$ | $=$ | $First_2(Field) \cup First_2(Method)$
$First_2(Field)$ | $=$ | $\{$ **`;#`** $\}$
$First_2(Method)$ | $=$ | $\{$ **`(`** $\} \times_2 First_2(Parameters) \times_2 \{$ **`)`** $\}$
$First_2(MainMethod)$ | $=$ | $\{$ **`static void`** $\}$
$First_2(MethodRest)$ | $=$ | $\{$ **`throws IDENT`** $\}$

### Cheap Trick for Basic Recursive Descent Implementation (not sure if enough for everything!)

Use $First_k(\chi Follow_k(X))$ with<br/>
* $(k=1)$ for almost all Nonterminals<br/>
* $(k=2)$ for $X = NewObjectArrayExpression$

$X$                         | $\chi$                                | $First_k(\chi Follow_k(X))$
:---                        | :---                                  |:---
$Program$                   | $ClassDeclaration$                    | $\{$ **`class`** $\}$
$ClassDeclaration$          | $ClassMember$                         | $\{$ **`public`** $\}$
$MethodPrefix$              | $FieldMethodPrefix$                   | $=First_1(Type)$
$MethodPrefix$              | $MainMethod$                          | $\{$ **`static`** $\}$
$FieldMethodRest$           | $Field$                               | $\{$ **`;`** $\}$
$FieldMethodRest$           | $Method$                              | $\{$ **`(`** $\}$
$MainMethod$                | $MethodRest$                          | $\{$ **`throws`** $\}$
$MainMethod$                | $Block$                               | $\{$ **`{`** $\}$
$Statement$                 | $Block$                               | $\{$ **`{`** $\}$
$Statement$                 | $EmptyStatement$                      | $\{$ **`;`** $\}$
$Statement$                 | $IfStatement$                         | $\{$ **`if`** $\}$
$Statement$                 | $ExpressionStatement$                 | $=First_1(PrimaryExpression)$
$Statement$                 | $WhileStatement$                      | $\{$ **`while`** $\}$
$Statement$                 | $ReturnStatement$                     | $\{$ **`return`** $\}$
$BlockStatement$            | $Statement$                           | $=First_1(Statement)$
$BlockStatement$            | $LocalVariableDeclarationStatement$   | $=First_1(Type)$
$Arguments$                 | $Expression$                          | $=First_1(PrimaryExpression)$
$NewObjectArrayExpression$  | $NewObjectExpression$                 | $\{$ **`IDENT (`** $\}$
$BlockStatement$            | $NewArrayExpression$                  | $\{$ **`int [`** $\|$ **`boolean [`** $\|$ **`void [`** $\|$ **`IDENT [`** $\}$


$X$                 | $First_1(X)$
:---                |:---
$Type$              | $\{$ **`int`** $\|$ **`boolean`** $\|$ **`void`** $\|$ **`IDENT`** $\}$
$Statement$         | $\{$ **`{`** $\|$ **`;`** $\|$ **`if`** $\|$ $First_1(PrimaryExpression)$ $\|$ **`IDENT`** $\}$
$PrimaryExpression$ | $\{$ **`null`** $\|$ **`false`** $\|$ **`true`** $\|$ **`INTEGER_LITERAL`** $\|$ **`IDENT`** $\|$ **`true`** $\}$