# MiniJava 4.0 - Abstrakte Algebra

Note: Use
* http://waylonflinn.github.io/markdown-it-katex/ or
* vscode or
* another katex-able Markdown-Viewer

to view this file

## Abstrakte Syntax als Signatur einer ordnungssortierten Termalgebra


$ $                                 | $ $   | $ $
---:                                | :---: |:---
$Program$                           | $::$  | $ClassDeclaration*$
$ClassDeclaration$                  | $::$  | **id** $ClassMember*$
$ClassMember$                       | $= $  | $Field$ $\|$ $Method$ $\|$ $MainMethod$
$Field$                             | $::$  | **id** $Type$
$Method$                            | $::$  | **id** $Type$ $Parameter*$ $Block$ **id**$?$
$MainMethod$                        | $::$  | **id** $Type$ $Parameter*$ $Block$ **id**$?$
$Parameter$                         | $::$  | **id** $Type$
$Block$                             | $::$  | $BlockStatement*$
$BlockStatement$                    | $= $  | $LocalVariableDeclarationStatement$ $\|$ $Statement$
$LocalVariableDeclarationStatement$ | $::$  | **id** $Type$ $Expression?$
$Type$                              | $= $  | $VoidType$ $\|$ $IntegerType$ $\|$ $BooleanType$ $\|$ $ArrayType$ $\|$ $ClassType$
$VoidType$                          | $::$  |
$IntegerType$                       | $::$  |
$BooleanType$                       | $::$  |
$ArrayType$                         | $::$  | $Type$
$ClassType$                         | $::$  | **id**
$Statement$                         | $= $  | $Block$ $\|$ $IfStatement$ $\|$ $WhileStatement$ $\|$ $ReturnStatement$ $\|$ $ExpressionStatement$
$IfStatement$                       | $::$  | $Expression$ $Statement$ $Statement?$
$WhileStatement$                    | $::$  | $Expression$ $Statement$
$ReturnStatement$                   | $::$  | $Expression?$
$ExpressionStatement$               | $::$  | $Expression$
$Expression$                        | $= $  | $BinaryExpression$ $\|$ $UnaryExpression$ $\|$ $MethodInvocationExpression$ $\|$ $FieldAccessExpression$ $\|$ $ArrayAccessExpression$ $\|$ $ReferenceExpression$ $\|$ $LiteralExpression$ $\|$ $NewObjectExpression$ $\|$ $NewArrayExpression$
$BinaryExpression$                  | $::$  | $Expression$ $Expression$ **binOp**
$UnaryExpression$                   | $::$  | $Expression$ **unOp**
$MethodInvocationExpression$        | $::$  | $Expression$ **id** $Expression*$
$FieldAccessExpression$             | $::$  | $Expression$ **id**
$ArrayAccessExpression$             | $::$  | $Expression$ $Expression$
$ReferenceExpression$               | $::$  | **id**
$LiteralExpression$                 | $::$  | **val**
$NewObjectExpression$               | $::$  | **id**
$NewArrayExpression$                | $::$  | $Type$ $Expression$

## Elemente des AST: Beschreibung


$ $                                 | Beschreibung
---:                                | :---
$Program$                           | Dies ist der Wurzelknoten des AST. Er stellt ein vollständiges Programm bestehend beliebig vielen Klassen dar.
$ClassDeclaration$                  | Entspricht einer benannten Klasse bestehend aus Feldern und Methoden.
$ClassMember$                       | Class member sind entweder Felder, Methoden oder Main-Methoden.
$Field$                             | Entspricht einem benannten Feld einer Klasse mit einem Typ.
$Method$                            | Entspricht einer benannten Methode einer Klasse. Diese hat einen Rückgabewert, beliebig viele Argumente und optional den Namen einer Exception aus der `throws`-Klausel. Außerdem beinhaltet jede Methode einen Block, der den Methoden-Rumpf darstellt.
$MainMethod$                        | Die Main-Methode ist der Einstiegspunkt in ein Programm. Sie besteht aus den gleichen Bestandteilen wie eine Methode, ihr Rückgabetyp ist jedoch immer `void`.
$Parameter$                         | Entspricht einem benannten Parameter einer (Main-)Methode mit einem Typ.
$Block$                             | Ein Block ist eine Sammlung von beliebig vielen Block-Statements.
$BlockStatement$                    | Ein Block-Statement ist ein Statement, welches direkt innerhalb eines Blocks vorkommt. Es ist entweder ein gemeines Statement oder alternativ die Deklaration einer lokalen Variable.
$LocalVariableDeclarationStatement$ | Entspricht der Deklaration einer lokalen Variable. Diese besitzt einen Namen, einen Typ sowie optional einen Ausdruck zur Initialisierung.
$Type$                              | Entspricht dem Typ einer Variable, eines Feldes oder des Rückgabe-Werts einer Methode. Ein Typ ist entweder VoidType, IntegerType, BooleanType, ArrayType oder ClassType.
$VoidType$                          | Der VoidType, gekennzeichnet durch das Schlüsselwort `void`, ist nach Spezifikation kein Typ im eigentlichen Sinne und wird bei der Deklaration von Methoden verwendet, um anzugeben, dass diese keinen Rückgabewert besitzen.
$IntegerType$                       | Entspricht einem ganzzahligen Typ, gekennzeichnet durch das Schlüsselwort `int`.
$BooleanType$                       | Entspricht einem booleschen Typ, gekennzeichnet durch das Schlüsselwort `boolean`.
$ArrayType$                         | Entspricht dem Typ eines Arrays, welcher eines der in "Type" festgelegten Elemente sein kann. 
$ClassType$                         | Entspricht dem Typ einer Klasse, durch eine Zeichenkette festgelegt.
$Statement$                         | Enstpricht einem gemeinen Block-Statement innerhalb eines Blocks, welches kein Variablen-Deklarations-Statement ist.
$IfStatement$                       | Entspricht einem Kontrollfluss-Verzweigungs-Statement. Das Element besteht aus einer Verzweigungsbedingung, einem Statement für den Fall, dass jene zu WAHR auswertet und einem optionalen Statement für den FALSCH-Fall.
$WhileStatement$                    | Entspricht einem While-Schleifen-Statement. Das Element besteht aus einer Sprungbedingung und einem Rumpf-Statement.
$ReturnStatement$                   | Entspricht einem Rückgabe-Statement, welches optional einen Ausdruck als Rückgabewert hat.
$ExpressionStatement$               | Entspricht einem Statement, welches aus einem Ausdruck besteht.
$Expression$                        | Ausdrücke sind entweder binäre/ unäre Ausdrücke, Methoden-Aufrufe, Feld-Zugriffe, Array-Zugriffe, Referenzen, Literale, Deklarationen neuer Objekte oder Deklarationen neuer Arrays.
$BinaryExpression$                  | Entspricht einem binären Ausdruck. Das Element besteht aus dem Binär-Operator und den Ausdrücken, auf die die Operation angewandt wird.
$UnaryExpression$                   | Entspricht einem unären Ausdruck. Das Element besteht aus dem Unär-Operator und dem Ausdruck, auf den die Operation angewandt wird.
$MethodInvocationExpression$        | Entspricht einem Methoden-Aufruf. Das Element besteht aus dem den "Parent" bestimmenden Ausdruck, dem Methoden-Bezeichner und einer Argument-Liste
$FieldAccessExpression$             | Entspricht einem Feld-Zugriff. Das Element besteht aus dem den "Parent" bestimmenden Ausdruck und dem Feld-Bezeichner.
$ArrayAccessExpression$             | Entspricht einem Array-Zugriff. Das Element besteht aus dem den "Parent" bestimmenden Ausdruck und einem Index.
$ReferenceExpression$               | Entspricht einer Referenz. Jene kann aus einer beliebigen Zeichenkette, "null", oder "this" bestehen.
$LiteralExpression$                 | Entspricht einem Literal. Jenes kann verschiedenen Types sein: boolsch, ganzzahlig, ...
$NewObjectExpression$               | Entspricht einer Deklaration eines neuen Objects. Das Element besteht aus einem Klassen-Bezeichner.
$NewArrayExpression$                | Entspricht einer Deklaration eines neuen Arrays. Das Element besteht aus einem Typ und einer Längenangabe.