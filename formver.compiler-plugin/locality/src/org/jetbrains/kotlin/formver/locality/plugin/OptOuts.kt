/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousInitializerSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.readonly.plugin.isInSpecification
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private fun optOutId(name: String) = ClassId(FqName("org.jetbrains.kotlin.formver.plugin"), Name.identifier(name))

/** Whether [this] or a class that contains it, directly or through nested classes, carries the annotation [id]. */
private fun FirBasedSymbol<*>.hasOwnOrClassAnnotation(id: ClassId, session: FirSession): Boolean =
    hasAnnotation(id, session) || getContainingClassSymbol()?.hasOwnOrClassAnnotation(id, session) == true

fun FirBasedSymbol<*>.neverConvert(session: FirSession) = hasOwnOrClassAnnotation(optOutId("NeverConvert"), session)

/** Whether [this] is not verified: it or a class containing it is `@NeverVerify` or `@NeverConvert`. */
fun FirBasedSymbol<*>.neverVerify(session: FirSession) =
    hasOwnOrClassAnnotation(optOutId("NeverVerify"), session) || neverConvert(session)

@OptIn(DirectDeclarationsAccess::class)
private fun FirClassSymbol<*>.hasNeverVerifyConstructor(session: FirSession) =
    declarationSymbols.any { it is FirConstructorSymbol && it.neverVerify(session) }

/**
 * Whether the code checked in [this] context, inside [declaration] when one is given, is trusted rather than checked:
 * it belongs to a declaration that is not verified, or to the construction of a class with a `@NeverVerify`
 * constructor, and lies outside a specification. docs/soundness.md says what such code is trusted to do.
 */
fun CheckerContext.isTrustedCode(declaration: FirBasedSymbol<*>? = null): Boolean {
    if (isInSpecification()) return false
    val chain = containingDeclarations + listOfNotNull(declaration)
    if (chain.any { it.neverVerify(session) }) return true
    // A property initializer or an `init` block runs as part of the construction; a property accessor does not.
    return chain.indices.any { i ->
        val owner = chain[i] as? FirClassSymbol<*> ?: return@any false
        val member = chain.getOrNull(i + 1)
        val inConstruction = member is FirAnonymousInitializerSymbol ||
            member is FirPropertySymbol && chain.getOrNull(i + 2) !is FirPropertyAccessorSymbol
        inConstruction && owner.hasNeverVerifyConstructor(session)
    }
}

/** Runs [checker] only on code that is not trusted. */
class UntrustedExpressionChecker<E : FirStatement>(private val checker: FirExpressionChecker<E>) :
    FirExpressionChecker<E>(checker.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: E) {
        if (!context.isTrustedCode()) checker.check(expression)
    }
}

/** Runs [checker] only on declarations whose code is not trusted. */
class UntrustedDeclarationChecker<D : FirDeclaration>(private val checker: FirDeclarationChecker<D>) :
    FirDeclarationChecker<D>(checker.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        if (!context.isTrustedCode(declaration.symbol)) checker.check(declaration)
    }
}
