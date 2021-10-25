package edu.kit.compiler

import java.io.File

class Compiler(
	private val config: Config
) {
	private val consoleOutputManager = ConsoleOutputManager(this.javaClass)

	fun compile(file: File) {
		if (config.isEcho) {
			file.useLines { lines -> lines.forEach { consoleOutputManager.println(it) } }
		}
	}

	interface Config {
		val isEcho: Boolean
	}
}
