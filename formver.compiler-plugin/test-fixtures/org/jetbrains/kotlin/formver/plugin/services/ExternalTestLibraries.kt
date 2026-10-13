/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotlin sources compiled without the plugin into class directories, standing in for dependencies whose bodies a
 * client module cannot see.
 */
object ExternalTestLibraries {
    private val compiled = ConcurrentHashMap<File, File>()

    /** The class directory compiled from [source], shared by every test of this JVM. */
    fun compiled(source: File): File = compiled.computeIfAbsent(source.canonicalFile, ::compile)

    private fun compile(source: File): File {
        val output = Files.createTempDirectory("formver-external-library").toFile()
        val stdlib = System.getProperty("org.jetbrains.kotlin.test.kotlin-stdlib")
            ?: error("org.jetbrains.kotlin.test.kotlin-stdlib is not set.")
        val arguments = K2JVMCompilerArguments().apply {
            freeArgs = listOf(source.path)
            destination = output.path
            classpath = stdlib
            noStdlib = true
            noReflect = true
            moduleName = source.nameWithoutExtension
        }
        val messages = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(PrintStream(messages), MessageRenderer.PLAIN_RELATIVE_PATHS, false)
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        check(exitCode == ExitCode.OK) { "Compiling external test library $source failed:\n$messages" }
        return output
    }
}
