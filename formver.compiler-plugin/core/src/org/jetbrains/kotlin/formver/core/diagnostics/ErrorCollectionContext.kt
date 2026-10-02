/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.diagnostics

import org.jetbrains.kotlin.KtSourceElement

/**
 * Surface for emitting conversion diagnostics, narrower than `ProgramConversionContext` so that
 * subordinate code (purity checks, embedding builders, etc.) can depend on just diagnostic
 * reporting without pulling in the rest of the conversion machinery.
 */
interface ErrorCollectionContext {
    /** Report a purity violation at [source]. */
    fun reportPurityViolation(source: KtSourceElement?, msg: String)

    /** Report code whose ownership the permission encoding cannot follow, at [source]. */
    fun reportUnsupportedOwnership(source: KtSourceElement?, msg: String)

    /** Warn that the write at [source] is dropped because the local it goes through is shared. */
    fun reportUntrackedWrite(source: KtSourceElement?)

    /** Report a minor internal error; the source is supplied by the implementation. */
    fun reportMinorInternalError(msg: String)
}
