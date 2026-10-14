/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.getSealedClassInheritors
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.core.embeddings.expression.*

/**
 * The value of the exhaustive [whenExpression] on the path where no branch matches, where Kotlin throws. The
 * closed-world facts of the subject's type and the failed branch conditions must show that path unreachable.
 */
internal fun StmtConversionContext.unmatchedExhaustiveWhen(
    whenExpression: FirWhenExpression, subject: VariableEmbedding?,
): ExpEmbedding {
    val facts = subject?.let { variable ->
        val subjectClass = whenExpression.subjectVariable?.returnTypeRef?.coneType?.toRegularClassSymbol(session)
        subjectClass?.let { closedWorldFacts(variable, it, mutableSetOf()) }
    }.orEmpty()
    return UnmatchedWhen(facts.toConjunction(), embedType(whenExpression)).withPosition(whenExpression.source)
}

/**
 * Facts about every value of [cls] that hold because its subclasses or entries are all known: a value of an enum
 * class is one of its entries, and a value of a sealed class or interface is a value of one of its direct
 * inheritors. [visited] guards against revisiting a class reached twice through a sealed hierarchy.
 */
@OptIn(DirectDeclarationsAccess::class)
private fun StmtConversionContext.closedWorldFacts(
    value: ExpEmbedding, cls: FirRegularClassSymbol, visited: MutableSet<FirRegularClassSymbol>,
): List<ExpEmbedding> {
    if (!visited.add(cls)) return emptyList()
    val isValue = Is(value, embedType(cls.defaultType()))
    return when {
        cls.classKind == ClassKind.ENUM_CLASS -> {
            val entries = cls.declarationSymbols.filterIsInstance<FirEnumEntrySymbol>()
            listOf(OperatorExpEmbeddings.Implies(isValue, entries.map { EqCmp(value, embedEnumEntry(it)) }.toDisjunction()))
        }
        cls.isSealed -> {
            @OptIn(SymbolInternals::class)
            val inheritors = cls.fir.getSealedClassInheritors(session)
                .map { session.symbolProvider.getClassLikeSymbolByClassId(it) as? FirRegularClassSymbol }
            // Without every inheritor the disjunction would leave values out, so no fact is stated.
            if (inheritors.any { it == null }) return emptyList()
            val isInheritor = inheritors.map { Is(value, embedType(it!!.defaultType())) }
            listOf(OperatorExpEmbeddings.Implies(isValue, isInheritor.toDisjunction())) +
                inheritors.flatMap { closedWorldFacts(value, it!!, visited) }
        }
        else -> emptyList()
    }
}
