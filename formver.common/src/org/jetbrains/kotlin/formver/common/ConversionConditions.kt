package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

enum class PurityRule { FUNCTION_BODY, LAW_BODY, ASSERT_CONDITION, REFUTE_CONDITION }
data class PurityCondition(val rule: PurityRule, val declaration: FirBasedSymbol<*>? = null) : KotlinCondition

/**
 * [MISSING_VARIANT]: a loop of a termination-checked function has no variant. [INT_MEASURE]: a measure of a function
 * that is not `@Pure`, or of a loop, is not an `Int`. [CALLEE]: a termination-checked function calls a source
 * function that may not terminate. [UNMEASURED_CYCLE]: it calls a function of its recursion cycle that has no
 * measure. [CYCLE_IN_LAMBDA]: a lambda in it
 * calls a function of its recursion cycle. [UNHANDLED]: it contains an element that the termination analysis does not
 * handle, of the kind [TerminationCondition.cycle] names. [INITIALIZERS]: the initializers that calling it may run
 * are not known to terminate.
 */
enum class TerminationRule {
    MISSING_MEASURE, DUPLICATE, MEASURE_TYPE, MISSING_VARIANT, INT_MEASURE, CALLEE, UNMEASURED_CYCLE, CYCLE_IN_LAMBDA, UNHANDLED, INITIALIZERS,
}

/**
 * A termination measure that is absent where recursion or a loop needs it, or is ill-formed, or a call that a
 * termination-checked function cannot show to terminate. [cycle] names the recursion, the callee, or the kind of an
 * unhandled element.
 */
data class TerminationCondition(val rule: TerminationRule, val cycle: List<String> = emptyList()) : KotlinCondition

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
