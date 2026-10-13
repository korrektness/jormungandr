/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.util.Collections
import java.util.IdentityHashMap

/** Lists the trusted bindings of each FIR registry once per compiler configuration. */
class ExternalSpecificationReporting(private val messages: MessageCollector) {
    private val manifested = Collections.newSetFromMap(IdentityHashMap<ExternalSpecificationRegistry, Boolean>())

    fun reportAssumptions(registry: ExternalSpecificationRegistry) {
        if (registry.errors.isNotEmpty() || !manifested.add(registry)) return
        for (binding in registry.assumptions) messages.report(CompilerMessageSeverity.INFO, "Trusted external binding: $binding")
    }
}
