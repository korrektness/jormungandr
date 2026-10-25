package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.readonly.plugin.isSpecificationCall

/** Stable source paths for locals in lexical read-only regions. Values are still evaluated at their declarations. */
class BorrowedViews(file: FirFile, context: CheckerContext) {
    private val session = context.session
    private val candidates = mutableMapOf<FirBasedSymbol<*>, FirExpression>()
    private val sources = mutableMapOf<FirBasedSymbol<*>, Path?>()
    private val readOnlyLocals = mutableSetOf<FirBasedSymbol<*>>()
    fun isReadOnlyLocal(symbol: FirBasedSymbol<*>): Boolean = symbol in readOnlyLocals
    init {
        file.accept(object : FirVisitorVoid() {
            private var depth = 0
            override fun visitElement(element: FirElement) {
                val opens = element.isSpecificationCall() || element is FirFunction && element.symbol.isPure(session)
                if (opens) depth++
                if (depth > 0 && element is FirProperty && element.isNamedLocal()) {
                    readOnlyLocals.add(element.symbol)
                    if (!element.isVar && !context(context) { element.returnTypeRef.coneType.isValueType() })
                        element.initializer?.let { candidates[element.symbol] = it }
                }
                element.acceptChildren(this)
                if (opens) depth--
            }
        })
    }

    private fun FirProperty.isNamedLocal(): Boolean = isLocal && !name.isSpecial

    private fun FirPropertySymbol.isStableField(): Boolean {
        if (!isVal || !isFinal || isOverride) return false
        return backingFieldSymbol != null && !isCustom
    }

    fun source(symbol: FirBasedSymbol<*>): Path? {
        if (symbol in sources) return sources[symbol]
        sources[symbol] = null // Break cyclic or incomplete local lineages.
        return candidates[symbol]?.let(::source).also { sources[symbol] = it }
    }

    fun source(expression: FirExpression): Path? = when (expression) {
        is FirSmartCastExpression -> source(expression.originalExpression)
        is FirCheckedSafeCallSubject -> source(expression.originalReceiverRef.value)
        is FirCheckNotNullCall -> source(expression.argumentList.arguments.single())
        is FirTypeOperatorCall -> if (expression.operation in setOf(FirOperation.AS, FirOperation.SAFE_AS))
            source(expression.argument) else null
        is FirThisReceiverExpression -> expression.calleeReference.symbol?.let { listOf(it) }
        is FirPropertyAccessExpression -> when (val symbol = expression.calleeReference.symbol) {
            is FirValueParameterSymbol, is FirReceiverParameterSymbol -> listOf(symbol)
            is FirPropertySymbol -> if (symbol.isLocal) source(symbol)
                else if (symbol.isStableField())
                    expression.dispatchReceiver?.let(::source)?.plus(symbol) else null
            else -> null
        }
        else -> null
    }
}

class BorrowedViewsResolver(session: FirSession) : FirExtensionSessionComponent(session) {
    private val cache = session.firCachesFactory.createCache { file: FirFile, context: CheckerContext -> BorrowedViews(file, context) }
    fun resolve(file: FirFile, context: CheckerContext): BorrowedViews = cache.getValue(file, context)
    companion object {
        fun getFactory(): Factory = Factory { BorrowedViewsResolver(it) }
    }
}

private val FirSession.borrowedViewsResolver: BorrowedViewsResolver by FirSession.sessionComponentAccessor()

context(context: CheckerContext)
@OptIn(SymbolInternals::class)
fun borrowedViews(): BorrowedViews = context.session.borrowedViewsResolver.resolve(requireNotNull(context.containingFileSymbol).fir, context)

context(context: CheckerContext)
fun FirBasedSymbol<*>.borrowedViewSource(): Path? = borrowedViews().source(this)
