/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.classLikeLookupTagIfAny
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.type.plugin.SymbolTypeFactResolver
import org.jetbrains.kotlin.name.ClassId

private val FirBasedSymbol<*>.declared: FirBasedSymbol<*>
    get() = (this as? FirCallableSymbol<*>)?.unwrapFakeOverrides() ?: this

/**
 * Whether [this] member function owns its dispatch receiver: it is annotated `@Unique`.
 */
fun FirBasedSymbol<*>.ownsDispatchReceiver(session: FirSession): Boolean =
    declared.hasAnnotation(defaultUniquenessAnnotationId, session)

/**
 * Whether [this] member function borrows its dispatch receiver: it is annotated `@Borrowed`, or it is a `@Pure`
 * function that owns it.
 */
fun FirBasedSymbol<*>.borrowsDispatchReceiver(session: FirSession): Boolean =
    declared.hasAnnotation(defaultLocalityAnnotationId, session) ||
            ownsDispatchReceiver(session) && declared.isPure(session)

/**
 * The declaration that binds `this` of [this] class where [context] checks: a member function or accessor of the class,
 * or one of its constructors, anonymous initializers or member properties. Null when none of them encloses the check.
 */
context(context: CheckerContext)
fun FirClassSymbol<*>.receiverOwner(): FirDeclaration? {
    for (element in context.containingElements.asReversed()) {
        when (element) {
            is FirClass -> if (element.symbol == this) return null
            is FirAnonymousFunction -> {}
            is FirConstructor -> if (element.symbol.containingClassLookupTag() == toLookupTag()) return element
            is FirFunction, is FirProperty ->
                if ((element as FirCallableDeclaration).dispatchReceiverType?.classLikeLookupTagIfAny == toLookupTag()) {
                    return element
                }
            is FirAnonymousInitializer -> if (element.containingDeclarationSymbol == this) return element
            else -> {}
        }
    }
    return null
}

/**
 * The locality of `this` of [this] class: local in a member that borrows it.
 */
context(context: CheckerContext)
fun FirClassSymbol<*>.resolveReceiverLocality(): Locality {
    val owner = receiverOwner() as? FirFunction ?: return Locality.Global
    return if (owner !is FirConstructor && owner.symbol.borrowsDispatchReceiver(context.session)) Locality.Local
    else Locality.Global
}

/**
 * The locality a callee requires of its dispatch receiver, or null when it declares none.
 */
object DispatchReceiverLocalityResolver : SymbolTypeFactResolver<Locality?, FirCallableSymbol<*>> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirCallableSymbol<*>): Locality? =
        when {
            symbol.borrowsDispatchReceiver(context.session) -> Locality.Local
            symbol.ownsDispatchReceiver(context.session) -> Locality.Global
            else -> null
        }
}

/**
 * Reports [factory] on a function-level [annotationId] of a function without a dispatch receiver: on a member
 * function the annotation describes `this`, which other functions lack.
 */
class DispatchReceiverAnnotationPlacementChecker(
    private val annotationId: ClassId,
    private val factory: KtDiagnosticFactory0,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration.dispatchReceiverType != null) return
        val annotation = declaration.getAnnotationByClassId(annotationId, context.session) ?: return
        reporter.reportOn(annotation.source, factory)
    }
}

val BorrowedPlacementChecker =
    DispatchReceiverAnnotationPlacementChecker(defaultLocalityAnnotationId, LocalityErrors.INVALID_LOCALITY_TYPE_TARGET)
