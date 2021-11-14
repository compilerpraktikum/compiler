# Compiler

This repository is part of a Uni-Project to write a complete Compiler for a
subset of Java.

## Features

- error recovery using context sensitive anchor sets
- source annotated error messages
- type-safe AST annotations and wrapping using `Wrapped`

## Documentation

- [MiniJava Syntax](./docs/parser/MiniJava%20Syntax.md): left-factorized Grammar, `First` and `Follow` sets
- [Lexer DEA](./docs/LexerDEA.svg): deterministic automata for the lexer
- [Wrapper AST Prototype](./docs/parser/WrappedAstPrototype.hs): Wrapper prototype written in haskell

## Building

```bash
$ ./build
# Or just:
$ ./gradlew shadowJar
# To build the `LexerDEA.svg` file:
$ 
```

## Testing

Our tests consist of unit tests written directly in kotlin and `*MjTestSuite`tests, that
run tests using all [MjTest-tests](https://git.scc.kit.edu/IPDSnelting/mjtest-tests) directly in kotlin.
This makes it easier so use the `mjtests` in the IDE.

```bash
# make sure the `test-cases` submodule is cloned
$ git submodule update --recursive
$ ./gradlew test
```

## Running

```bash
$ ./run <arguments>
# Or just
$ java -jar out/libs/compiler-all.jar <arguments>
```


## Structure

The repository is organized into packages:
- ast: The `AST` type, Visitor and `Wrapper` type
- error: Functionality for attaching Errors and Warnings to source-locations
- lex: The lexer and functionality for getting the current source-location
- parser
- prettyprinter: PrettyPrinting Visitor