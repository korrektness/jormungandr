/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.common.SemanticOptIn
import org.jetbrains.kotlin.formver.common.TrustedAssumption
import org.jetbrains.kotlin.formver.common.trustName
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineCallable
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.isNonRetainingStdlibMember
import org.jetbrains.kotlin.formver.core.verificationSkipped
import org.jetbrains.kotlin.formver.locality.plugin.neverConvert
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * The trusted assumptions that a program relies on, beyond the declarations it verifies. [verifiedHere] tells whether
 * the program verifies a callee itself, and [verified] gives the declaration the program verifies, if any.
 */
class TrustedAssumptionCollector(
    private val session: FirSession,
    private val typeResolver: TypeResolver,
    private val verifiedHere: (FirCallableSymbol<*>) -> Boolean,
    private val verified: () -> FirCallableSymbol<*>?,
) {
    private val trusted = sortedSetOf<TrustedAssumption>()

    /** The source callees whose contracts the program uses, each with a name the report gives it. */
    private val sourceCallees = mutableSetOf<Pair<FirCallableSymbol<*>, FirCallableSymbol<*>>>()

    /**
     * The recorded assumptions, with each source callee that SnaKt skipped and each non-retaining library callee that
     * the verified declaration calls or reads. Whether a callee is skipped is known once its own check has run, so the
     * set is complete only after every checker has run.
     */
    val assumptions: Set<TrustedAssumption>
        @OptIn(SymbolInternals::class)
        get() = trusted + nonRetainingCallees() + sourceCallees.filter { (callee, _) -> callee.fir.verificationSkipped == true }
            .map { (_, name) -> TrustedAssumption(TrustedAssumption.Kind.SKIPPED, name.trustName) }

    @OptIn(SymbolInternals::class)
    private fun nonRetainingCallees(): List<TrustedAssumption> {
        val declaration = verified()?.fir ?: return emptyList()
        val callees = mutableSetOf<FirCallableSymbol<*>>()
        declaration.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirQualifiedAccessExpression) element.toResolvedCallableSymbol()?.let(callees::add)
                element.acceptChildren(this)
            }
        })
        return callees.filter { it.isNonRetainingStdlibMember(session) }
            .map { TrustedAssumption(TrustedAssumption.Kind.NON_RETAINING_LIBRARY_CALL, it.trustName) }
    }

    val semanticOptIns = sortedSetOf<SemanticOptIn>()

    /** Records an exact arithmetic mode so the report exposes where overflow obligations are disabled. */
    fun recordWrappingArithmetic(symbol: FirCallableSymbol<*>, kind: SemanticOptIn.Kind) {
        semanticOptIns += SemanticOptIn(kind, symbol.trustName)
    }

    /** Records the executed element equality assumption of [symbol]. */
    fun recordElementEquality(symbol: FirCallableSymbol<*>) {
        trusted += TrustedAssumption(TrustedAssumption.Kind.ELEMENT_EQUALITY, symbol.trustName)
    }

    /** Records that the termination-checked function of the program assumes that [symbol], a library callable, terminates. */
    fun recordAssumedTermination(symbol: FirCallableSymbol<*>) {
        trusted += TrustedAssumption(TrustedAssumption.Kind.ASSUMED_TERMINATING, symbol.trustName)
    }

    /** Records [symbol], a library callable that the program calls or reads as the opaque library call. */
    fun recordOpaqueLibraryCall(symbol: FirCallableSymbol<*>) {
        trusted += TrustedAssumption(TrustedAssumption.Kind.OPAQUE_LIBRARY_CALL, symbol.trustName)
    }

    /**
     * Records the external binding of [target]. A binding of `List.iterator` to the bundled model also records that
     * the model assumes the list is not mutated through any reference while the iterator is live.
     */
    fun recordBinding(target: FirBasedSymbol<*>) {
        val assumption = session.externalSpecifications!!.bindingAssumption(target)
        trusted += assumption
        val bundled = assumption.kind == TrustedAssumption.Kind.BUNDLED_SPEC
        if (bundled && target is FirCallableSymbol<*> && target.unwrapFakeOverrides().callableId == LIST_ITERATOR) {
            trusted += TrustedAssumption(TrustedAssumption.Kind.SNAPSHOT_ITERATOR, assumption.name)
        }
    }

    /**
     * Records the external binding of the function [target] as [recordBinding] does, and the `throwsOnlyIf` condition
     * of [model], the callable its binding gives it, under the bound declaration's name when [model] has one.
     */
    fun recordFunctionBinding(target: FirFunctionSymbol<*>, model: CallableEmbedding) {
        recordBinding(target)
        val binding = session.externalSpecifications!!.bindingAssumption(target)
        if ((model as? NonInlineCallable)?.throwsCondition != null) {
            trusted += TrustedAssumption(TrustedAssumption.Kind.THROWS_CONDITION, binding.name)
        }
    }

    /** Records [symbol], a callee or accessor that the program calls, when SnaKt does not verify it. */
    fun recordOptOut(symbol: FirCallableSymbol<*>, name: FirCallableSymbol<*> = symbol) {
        if (verifiedHere(symbol)) return
        if (!symbol.neverVerify(session)) {
            if (symbol.origin == FirDeclarationOrigin.Source) sourceCallees += symbol to name
            return
        }
        val kind = if (symbol.neverConvert(session)) TrustedAssumption.Kind.NEVER_CONVERT else TrustedAssumption.Kind.NEVER_VERIFY
        trusted += TrustedAssumption(kind, name.trustName)
    }

    /** Records the custom accessors of [property] that SnaKt does not verify, under the property's name. */
    fun recordAccessorOptOuts(property: FirPropertySymbol) {
        for (accessor in listOfNotNull(property.getterSymbol, property.setterSymbol)) {
            @OptIn(SymbolInternals::class)
            if (accessor.fir !is FirDefaultPropertyAccessor) recordOptOut(accessor, property)
        }
    }

    /**
     * Records the contract that [StdLibConverter.kt] gives [signature], the signature of [symbol], when [symbol] is a
     * library callable. A source declaration verifies its own body against that contract. An external spec model is a
     * source declaration too: its contract comes from the spec, and [recordBinding] records it.
     */
    fun recordLibraryContract(symbol: FirCallableSymbol<*>, signature: NamedFunctionSignature) {
        if (symbol.origin == FirDeclarationOrigin.Source) return
        if (signature.stdLibPreconditions(typeResolver).isEmpty() &&
            signature.stdLibPostconditions(signature.returns, typeResolver).isEmpty()) return
        trusted += TrustedAssumption(TrustedAssumption.Kind.LIBRARY_CONTRACT, symbol.trustName)
    }
}

private val LIST_ITERATOR = CallableId(StandardClassIds.List, Name.identifier("iterator"))
