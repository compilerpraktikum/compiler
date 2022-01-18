# Compiler

This repository is part of a [Uni-Project](https://pp.ipd.kit.edu/lehre/WS202122/compprakt/) to write a complete Compiler for a subset of Java.

## Features

- error recovery using context sensitive anchor sets
- source annotated error messages

## Documentation

- [MiniJava Syntax](./docs/parser/MiniJava%20-%20Syntax.md): left-factorized Grammar, `First` and `Follow` sets
- [Lexer DEA](./docs/LexerDEA.svg): deterministic automata for the lexer
- [Wrapper AST Prototype](./docs/parser/WrappedAstPrototype.hs): Wrapper prototype written in haskell

## Building

```bash
$ ./build
# Or just:
$ ./gradlew shadowJar
# To build the `LexerDEA.svg` file:
$ dot LexerDEA.dot -T svg > LexerDEA.svg
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
Use `--help` for information on CLI arguments.

## Structure

The repository is organized into packages:
- ast: abstract syntax tree
- backend: assembly generation from FIRM graph
- error: error handling and output
- lexer: lexer for input tokenization
- optimization: program optimization on FIRM graph
- parser: parser for AST construction
- semantic: semantic analysis (types, names, lvalue, etc.)
- source: input handling + source file annotations
- transform: AST to FIRM graph conversion
