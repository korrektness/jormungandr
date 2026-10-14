/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import java.io.File

/**
 * Reports what [ledger] collected once the checkers are done: it writes the report to [target], when given, and its
 * summary to [messages] at INFO. Code generation, and so this extension, runs only for a compilation without errors.
 */
class TrustReportWriter(
    private val ledger: TrustLedger,
    private val target: File?,
    private val messages: MessageCollector,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val compiled = ledger.snapshot()
        if (target == null) {
            messages.report(CompilerMessageSeverity.INFO, compiled.summary)
            return
        }
        val recompiled = moduleFragment.files.mapTo(HashSet()) { it.fileEntry.name }
        val report = writeTrustReport(target, compiled, recompiled)
        messages.report(CompilerMessageSeverity.INFO, "${report.summary} Details: $target")
    }
}
