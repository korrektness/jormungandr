package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

enum class PurityRule { FUNCTION_BODY, LAW_BODY, ASSERT_CONDITION, REFUTE_CONDITION }
data class PurityCondition(val rule: PurityRule, val declaration: FirBasedSymbol<*>? = null) : KotlinCondition

/** Kotlin provenance of a converted receiver, without embedding or backend dependencies. */
data class ConversionOwnershipPath(val root: Root, val members: List<Member> = emptyList()) {
    data class Root(val kind: Kind, val symbol: FirBasedSymbol<*>? = null, val name: String? = null)
    enum class Kind { DISPATCH_RECEIVER, RESULT, LOCAL, SYNTHETIC }
    data class Member(val symbol: FirPropertySymbol?, val name: String?)
}

sealed interface ConversionOwnershipCondition : KotlinCondition {
    enum class ReadKind { VAR_PROPERTY, ARRAY_ELEMENT, ARRAY_CONTENTS, STRING_BUILDER }
    enum class ReadReason { NEEDS_UNIQUE_RECEIVER, CONSUMED_ROOT }
    data class Read(
        val kind: ReadKind,
        val reason: ReadReason,
        val receiver: ConversionOwnershipPath?,
        val property: FirPropertySymbol? = null,
    ) : ConversionOwnershipCondition
    data class ConsumedArgument(
        val argument: ConversionOwnershipPath,
        val callee: FirFunctionSymbol<*>?,
    ) : ConversionOwnershipCondition
    enum class MissingFactsRule { INLINE_FUNCTION, LAMBDA_BODY }
    data class MissingFacts(
        val rule: MissingFactsRule,
        val declaration: FirFunctionSymbol<*>,
        val declarationSource: KtSourceElement? = null,
    ) : ConversionOwnershipCondition
    data object TryWithOwnership : ConversionOwnershipCondition
}

fun purityFailure(source: KtSourceElement?, condition: PurityCondition): Failure =
    Failure(FailureKind.PURITY_VIOLATION, source, condition)

fun unsupportedOwnershipFailure(source: KtSourceElement?, condition: ConversionOwnershipCondition): Failure =
    Failure(FailureKind.UNSUPPORTED_OWNERSHIP, source, condition,
        related = when (condition) {
            is ConversionOwnershipCondition.MissingFacts -> listOfNotNull(condition.declarationSource?.let {
                RelatedLocation("analysis declaration", it)
            })
            is FoldOwnershipCondition -> listOfNotNull(condition.absence?.site?.takeUnless { it == source }?.let {
                RelatedLocation("ownership loss", it)
            })
            else -> emptyList()
        })
