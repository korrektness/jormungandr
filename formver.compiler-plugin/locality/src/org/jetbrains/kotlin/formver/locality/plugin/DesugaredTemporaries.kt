/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.SpecialNames

/**
 * The receiver that [this] symbol stands for when it is a temporary that FIR introduces for a compound assignment or
 * an increment: `<array>` in `a[i] += v` or `a[i]++`, and `<receiver>` in `r.p += v` or `r.p++`. `null` for any other
 * symbol.
 *
 * The temporary is only used as the receiver of the accesses it was introduced for, so it is an alias of its
 * initializer.
 */
val FirBasedSymbol<*>.receiverTemporaryInitializer: FirExpression?
    get() = (this as? FirPropertySymbol)
        ?.takeIf { it.isLocal && (it.name == SpecialNames.ARRAY || it.name == SpecialNames.RECEIVER) }
        ?.resolvedInitializer
