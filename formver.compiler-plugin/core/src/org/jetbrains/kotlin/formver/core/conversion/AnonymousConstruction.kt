/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FunctionExp
import org.jetbrains.kotlin.formver.core.embeddings.expression.InlineCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.toBlock
import org.jetbrains.kotlin.formver.core.embeddings.types.asTypeEmbedding
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isManual
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.signatureParameterSymbols
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.uniqueness.plugin.resolveConstructionEscape
import org.jetbrains.kotlin.name.StandardClassIds

internal data class AnonymousSuperclass(
    val owner: FirRegularClassSymbol,
    val constructor: FirConstructorSymbol,
    val delegation: FirDelegatedConstructorCall,
)

/** The superclass call supported by both nominal type embedding and construction of this literal. */
context(program: ProgramConversionContext)
@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
internal fun FirAnonymousObject.eligibleSuperclass(): AnonymousSuperclass? =
    program.typeResolver.anonymousSuperclass(symbol) {
        val primary = declarations.singleOrNull() as? FirConstructor ?: return@anonymousSuperclass null
        if (!primary.isPrimary || primary.body != null) return@anonymousSuperclass null
        val delegation = primary.delegatedConstructor ?: return@anonymousSuperclass null
        val constructor = delegation.calleeReference.toResolvedConstructorSymbol() ?: return@anonymousSuperclass null
        val owner = symbol.resolvedSuperTypes.singleOrNull()?.toRegularClassSymbol(program.session)
            ?: return@anonymousSuperclass null
        if (owner.classKind != ClassKind.CLASS || owner != constructor.resolvedReturnType.toRegularClassSymbol(program.session)) {
            return@anonymousSuperclass null
        }
        if (!constructor.supportsAnonymousArguments() || !owner.supportsAnonymousConstruction()) return@anonymousSuperclass null
        if (constructionFailures(owner, program.session).isNotEmpty()) return@anonymousSuperclass null
        context(program.checkerContext) {
            if (owner.interfaceLaws().isNotEmpty()) return@anonymousSuperclass null
            if (owner.isAbstract && owner.inheritedImplementationObligations().isNotEmpty()) return@anonymousSuperclass null
        }
        AnonymousSuperclass(owner, constructor, delegation)
    }

context(program: ProgramConversionContext)
private fun FirConstructorSymbol.supportsAnonymousArguments(): Boolean =
    contextParameterSymbols.isEmpty() && context(program.checkerContext) {
        signatureParameterSymbols.none { it.isUnique() || it.isBorrowed() }
    }

context(program: ProgramConversionContext)
@OptIn(SymbolInternals::class)
private fun FirRegularClassSymbol.supportsAnonymousConstruction(): Boolean {
    if (classId == StandardClassIds.Any) return true
    val session = program.session
    if (origin != FirDeclarationOrigin.Source || classId.isLocal || isInner) return false
    if (typeParameterSymbols.isNotEmpty() || isManual(session) || session.externalSpecifications?.modelClass(this) != null) return false
    if (context(program.checkerContext) { hasObserverFamily() }) return false
    if (SpecifiedLambda.collect(fir, classId.embedName()).isNotEmpty()) return false
    return resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(session) }
        .filter { it.classKind == ClassKind.CLASS }.all { it.supportsAnonymousConstruction() }
}

/** Constructs an object with no initialization or dispatch of its own, on the same receiver as its superclass. */
internal fun StmtConversionContext.convertAnonymousObject(expression: FirAnonymousObjectExpression): ExpEmbedding? {
    val literal = expression.anonymousObject
    val (owner, parent, delegation) = with(this) { literal.eligibleSuperclass() } ?: return null
    fun unsupported(shape: String): Nothing = throw UnsupportedFeatureException(expression.source, shape)
    if (!typeResolver.activeAnonymousConstruction.add(literal.symbol)) unsupported("recursive anonymous object construction")
    try {
        val type = typeResolver.embedAnonymousClass(literal.symbol) { embedType(it).pretype.name }
        val target = returnTargetProducer.getFresh(type.asTypeEmbedding())
        val signature = with(this) { parent.toFunctionSignature().signature }
        val construction = Construction(this, signature, target, exceptionEscape,
            { unsupported("anonymous superclass initialization with ownership errors") }, AnonymousSuperCall(this, delegation))
        return InlineCall(emptyList(), emptyList(), FunctionExp(null, construction.build(owner, parent).toBlock(), target.label!!),
            target.variable, target.variable, returnsUnique = parent.resolveConstructionEscape(session) == null,
            abandonConstructionOnExit = true)
    } finally {
        typeResolver.activeAnonymousConstruction.remove(literal.symbol)
    }
}
