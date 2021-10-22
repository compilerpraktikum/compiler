package edu.kit.compiler

import java.io.File

class Compiler(
	private val config: Config
) {
	fun compile(file: File) {
		if (config.isEcho) {
			file.useLines { lines -> lines.forEach { println(it) } }
		}
	}
}
