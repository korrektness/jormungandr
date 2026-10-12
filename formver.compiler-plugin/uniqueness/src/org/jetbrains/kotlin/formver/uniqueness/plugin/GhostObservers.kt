/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private val ghostObserverId = ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.GhostObserver"))

fun FirCallableSymbol<*>.isGhostObserver(session: FirSession): Boolean = hasAnnotation(ghostObserverId, session)

context(context: CheckerContext)
@OptIn(SymbolInternals::class)
fun FirSimpleFunction.ghostObserverViolation(): String? {
    val owner = symbol.getContainingClassSymbol() as? FirRegularClassSymbol
    val statement = body?.statements?.singleOrNull()
    val placeholder = (if (statement is FirReturnExpression) statement.result else statement) as? FirFunctionCall
    val validBody = (placeholder?.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)
        ?.callableId == CallableId(FqName("org.jetbrains.kotlin.formver.plugin"), Name.identifier("ghostObserver"))
    return when {
        owner?.fir?.isInterface != true || !owner.fir.status.isFun -> "Ghost observer outside a fun interface."
        !symbol.hasAnnotation(ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.Pure")), context.session) -> "Missing @Pure on ghost observer."
        status.isSuspend -> "Suspend ghost observer."
        symbol.hasAnnotation(ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.Law")), context.session) -> "Ghost observer marked @Law."
        valueParameters.isNotEmpty() || typeParameters.isNotEmpty() || receiverParameter != null ||
            contextParameters.isNotEmpty() -> "Ghost observer parameters."
        !validBody -> "Non-placeholder ghost observer body."
        else -> null
    }
}
