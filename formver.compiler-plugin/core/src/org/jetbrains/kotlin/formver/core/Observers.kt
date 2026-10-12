/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.unwrapUseSiteSubstitutionOverrides
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

/** Whether this member is an abstract `@Pure` function or `val` declared in an interface. */
context(context: CheckerContext)
fun FirCallableSymbol<*>.isObserver(): Boolean =
    isAbstract && isPure(context.session) &&
            (this is FirNamedFunctionSymbol || (this is FirPropertySymbol && !isVar)) &&
            (getContainingClassSymbol() as? FirClassSymbol<*>)?.classKind?.isInterface == true

/** Whether this member is an observer or overrides one, directly or through other overrides. */
context(context: CheckerContext)
fun FirCallableSymbol<*>.isObserverOrOverride(): Boolean =
    isObserver() || directOverriddenSymbolsSafe().any { it.isObserverOrOverride() }

/** Observer snapshots refer to declarations independently of call-site type arguments. */
context(context: CheckerContext)
fun FirFunctionSymbol<*>.observerDeclaration(): FirFunctionSymbol<*>? {
    val declaration = unwrapUseSiteSubstitutionOverrides()
    return declaration.takeIf { it != this && it.isObserverOrOverride() }
}
