/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.InlineStatus
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.memberDeclarationNameOrNull
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.borrowsDispatchReceiver
import org.jetbrains.kotlin.formver.locality.plugin.locality

/**
 * How the construction of [escapingClass] lets the object under construction escape. Its constructors then return
 * the object shared, since code outside the class may hold it.
 */
data class ConstructionEscape(val escapingClass: FirRegularClassSymbol, val kind: Kind, val member: FirCallableSymbol<*>?) {
    enum class Kind {
        /** A member that may store its receiver is used on `this`. */
        MemberCall,

        /** `this` itself escapes. */
        Use,

        /** A declaration that may run after construction references `this`. */
        Capture,
    }
}

val ConstructionEscapeRenderer = Renderer<ConstructionEscape> { escape ->
    val owner = escape.escapingClass.name.asString()
    when (escape.kind) {
        ConstructionEscape.Kind.MemberCall ->
            "the construction of '$owner' calls member '${escape.member?.memberDeclarationNameOrNull}' on 'this'"
        ConstructionEscape.Kind.Use ->
            "the construction of '$owner' lets 'this' escape"
        ConstructionEscape.Kind.Capture ->
            "the construction of '$owner' captures 'this' in a declaration that is not called in place"
    }
}

/**
 * Finds how the construction of a class lets the object under construction escape, if it does. SPECIFICATIONS.md
 * states when construction lets `this` escape.
 */
class ConstructionEscapeResolver(session: FirSession) : FirExtensionSessionComponent(session) {
    companion object {
        fun getFactory(): Factory = Factory { session -> ConstructionEscapeResolver(session) }
    }

    private val cache: FirCache<FirRegularClassSymbol, ConstructionEscape?, Nothing?> =
        session.firCachesFactory.createCache { symbol -> symbol.findConstructionEscape() }

    fun resolve(symbol: FirRegularClassSymbol): ConstructionEscape? = cache.getValue(symbol, null)

    @OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
    private fun FirRegularClassSymbol.findConstructionEscape(): ConstructionEscape? {
        if (classKind != ClassKind.CLASS || origin != FirDeclarationOrigin.Source) return null

        for (superType in resolvedSuperTypes) {
            val superClass = superType.toRegularClassSymbol(session) ?: continue
            resolve(superClass)?.let { return it }
        }

        val finder = EscapeFinder(this, session)
        for (declaration in fir.declarations) {
            when (declaration) {
                is FirConstructor -> {
                    declaration.delegatedConstructor?.accept(finder)
                    declaration.body?.accept(finder)
                }
                is FirAnonymousInitializer -> declaration.body?.accept(finder)
                is FirProperty -> {
                    declaration.initializer?.accept(finder)
                    declaration.delegate?.accept(finder)
                }
                else -> {}
            }
            finder.escape?.let { return it }
        }
        return null
    }
}

private val FirSession.constructionEscapeResolver: ConstructionEscapeResolver
        by FirSession.sessionComponentAccessor()

/**
 * How the construction of the class [this] constructor belongs to lets the object escape, or null when the
 * constructor's result is unique.
 */
fun FirConstructorSymbol.resolveConstructionEscape(session: FirSession): ConstructionEscape? {
    val classSymbol = resolvedReturnType.toRegularClassSymbol(session) ?: return null
    return session.constructionEscapeResolver.resolve(classSymbol)
}

private class EscapeFinder(
    private val classSymbol: FirRegularClassSymbol,
    private val session: FirSession,
) : FirVisitorVoid() {
    var escape: ConstructionEscape? = null

    private fun FirExpression.isThis(): Boolean =
        this is FirThisReceiverExpression && calleeReference.symbol == classSymbol

    private fun escape(kind: ConstructionEscape.Kind, member: FirCallableSymbol<*>? = null) {
        if (escape == null) escape = ConstructionEscape(classSymbol, kind, member)
    }

    /**
     * Whether an access to [this] property on the object under construction reads or writes its backing field. An
     * access reaches the resolved declaration when nothing can override it, and nothing can override a member of a
     * final class.
     */
    private val FirPropertySymbol.isField: Boolean
        @OptIn(SymbolInternals::class)
        get() = !isAbstract && (isFinal || classSymbol.isFinal) &&
                getterSymbol?.fir.let { it == null || it is FirDefaultPropertyGetter } &&
                setterSymbol?.fir.let { it == null || it is FirDefaultPropertySetter }

    override fun visitElement(element: FirElement) {
        if (escape != null) return
        when (element) {
            is FirThisReceiverExpression -> if (element.isThis()) escape(ConstructionEscape.Kind.Use)
            is FirFunctionCall -> visitAccess(element)
            is FirPropertyAccessExpression -> visitAccess(element)
            is FirAnonymousFunction ->
                if (element.inlineStatus == InlineStatus.Inline || element.invocationKind != null) {
                    element.acceptChildren(this)
                } else {
                    visitCapturing(element)
                }
            is FirFunction, is FirClass -> visitCapturing(element)
            else -> element.acceptChildren(this)
        }
    }

    private fun visitAccess(access: FirQualifiedAccessExpression) {
        val dispatchReceiver = access.dispatchReceiver
        if (dispatchReceiver != null && dispatchReceiver.isThis()) {
            val callee = access.toResolvedCallableSymbol()
            val callsMember = callee !is FirPropertySymbol || !callee.isField
            if (callsMember && callee?.borrowsDispatchReceiver(session) != true) {
                escape(ConstructionEscape.Kind.MemberCall, callee)
            }
        } else {
            dispatchReceiver?.accept(this)
        }
        access.extensionReceiver?.takeIf { it !== dispatchReceiver }?.accept(this)
        access.contextArguments.forEach { it.accept(this) }

        if (access is FirFunctionCall) {
            val mapping = access.resolvedArgumentMapping ?: return access.argumentList.accept(this)
            for ((argument, parameter) in mapping) {
                val borrowed = parameter.returnTypeRef.coneType.locality == Locality.Local
                if (borrowed && argument.unwrapArgument().isThis()) continue
                argument.accept(this)
            }
        }
    }

    private fun visitCapturing(element: FirElement) {
        element.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (escape != null) return
                if (element is FirExpression && element.isThis()) escape(ConstructionEscape.Kind.Capture)
                element.acceptChildren(this)
            }
        })
    }
}
