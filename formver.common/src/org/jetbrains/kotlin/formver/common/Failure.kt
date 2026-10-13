package org.jetbrains.kotlin.formver.common

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType

object FailureDiagnostics : org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer() {
    override fun getRendererFactory() = FailureToFirRenderer
}

enum class FailureCategory { PROOF, TRANSLATION, TOOL, SPECIFICATION, INVALID_INPUT }

/** Diagnostic identity, severity and highlighting policy have one registry. */
enum class FailureKind(
    val severity: Severity = Severity.ERROR,
    val declarationName: Boolean = false,
    val category: FailureCategory = FailureCategory.PROOF,
) {
    LOCALITY_MISMATCH(category = FailureCategory.INVALID_INPUT),
    CONTEXT_LOCALITY_MISMATCH(category = FailureCategory.INVALID_INPUT),
    INVALID_LOCALITY_CAPTURE(category = FailureCategory.INVALID_INPUT),
    INVALID_LOCALITY_TYPE_TARGET(category = FailureCategory.INVALID_INPUT),
    LOCALITY_CONTRACT_MISMATCH(category = FailureCategory.INVALID_INPUT),
    CONTEXT_LOCALITY_CONTRACT_MISMATCH(category = FailureCategory.INVALID_INPUT),
    UNIQUENESS_MISMATCH(category = FailureCategory.INVALID_INPUT),
    CONTEXT_UNIQUENESS_MISMATCH(category = FailureCategory.INVALID_INPUT),
    SHARED_CONSTRUCTION_MISMATCH(category = FailureCategory.INVALID_INPUT),
    ESCAPE_UNIQUENESS_INCONSISTENCY(category = FailureCategory.INVALID_INPUT),
    CONTEXT_ESCAPE_UNIQUENESS_INCONSISTENCY(category = FailureCategory.INVALID_INPUT),
    EXIT_UNIQUENESS_INCONSISTENCY(category = FailureCategory.INVALID_INPUT),
    INVALID_DUPLICATE_UNIQUE_ARGUMENT(category = FailureCategory.INVALID_INPUT),
    INVALID_OVERLAPPING_UNIQUE_ARGUMENTS(category = FailureCategory.INVALID_INPUT),
    INVALID_MOVED_ACCESS(category = FailureCategory.INVALID_INPUT),
    INVALID_UNIQUENESS_TYPE_TARGET(category = FailureCategory.INVALID_INPUT),
    INVALID_TYPE_PARAMETER_UNIQUENESS(category = FailureCategory.INVALID_INPUT),
    INVALID_VALUE_TYPE_UNIQUENESS(category = FailureCategory.INVALID_INPUT),
    OVERRIDE_UNIQUENESS_MISMATCH(category = FailureCategory.INVALID_INPUT),
    ACTUAL_UNIQUENESS_MISMATCH(category = FailureCategory.INVALID_INPUT),
    INVALID_UNIQUENESS_CAPTURE(category = FailureCategory.INVALID_INPUT),
    INVALID_PURE_UNIQUE_RESULT(category = FailureCategory.INVALID_INPUT),
    INVALID_PURE_REFERENCE_RESULT(category = FailureCategory.INVALID_INPUT),
    INVALID_UNIQUE_PRED_PLACEMENT(category = FailureCategory.INVALID_INPUT),
    CONDITIONAL_EFFECT_ERROR,
    VIPER_VERIFICATION_ERROR,
    VERIFICATION_CONDITION_NOT_ESTABLISHED,
    POSSIBLE_INDEX_OUT_OF_BOUND,
    UNEXPECTED_RETURNED_VALUE,
    INVALID_SUBLIST_RANGE,
    OWNERSHIP_NOT_ESTABLISHED,
    OVERRIDE_NOT_REFINING(declarationName = true),
    TERMINATION_NOT_ESTABLISHED,
    INVALID_SPEC_OF(category = FailureCategory.SPECIFICATION),
    INVALID_EXPOSES(category = FailureCategory.SPECIFICATION),
    INVALID_OBSERVER(category = FailureCategory.SPECIFICATION),
    INVALID_LAW(category = FailureCategory.SPECIFICATION),
    PURITY_VIOLATION(category = FailureCategory.TRANSLATION),
    MISSING_TERMINATION_MEASURE(declarationName = true, category = FailureCategory.TRANSLATION),
    INVALID_TERMINATION_MEASURE(category = FailureCategory.TRANSLATION),
    UNSUPPORTED_OWNERSHIP(category = FailureCategory.TRANSLATION),
    UNTRACKED_WRITE(Severity.WARNING, category = FailureCategory.TRANSLATION),
    UNSUPPORTED_FEATURE(Severity.WARNING, category = FailureCategory.TRANSLATION),
    VERIFICATION_SKIPPED(Severity.WARNING, declarationName = true, category = FailureCategory.TRANSLATION),
    INTERNAL_ERROR(category = FailureCategory.TOOL),
    VERIFIER_UNAVAILABLE(category = FailureCategory.TOOL),
    INVALID_VIPER_PROGRAM(category = FailureCategory.TOOL);

    val factory: KtDiagnosticFactory1<Failure> by lazy {
        KtDiagnosticFactory1(
            name, severity,
            if (declarationName) SourceElementPositioningStrategies.DECLARATION_NAME else SourceElementPositioningStrategies.DEFAULT,
            PsiElement::class, FailureToFirRenderer,
        )
    }
}

sealed interface KotlinPredicate {
    data class Symbol(val symbol: FirBasedSymbol<*>) : KotlinPredicate
    data class Constant(val value: Boolean) : KotlinPredicate
    data class IsNull(val symbol: FirBasedSymbol<*>, val negated: Boolean) : KotlinPredicate
    data class IsType(val symbol: FirBasedSymbol<*>, val type: ConeKotlinType, val negated: Boolean) : KotlinPredicate
    data class Negation(val predicate: KotlinPredicate) : KotlinPredicate
    data class Conjunction(val left: KotlinPredicate, val right: KotlinPredicate) : KotlinPredicate
    data class Disjunction(val left: KotlinPredicate, val right: KotlinPredicate) : KotlinPredicate
}

sealed interface KotlinCondition {
    data class SpecOf(val rule: SpecOfRule, val targetName: String? = null) : KotlinCondition
    data class Exposes(val rule: ExposesRule) : KotlinCondition
    data class Law(val rule: LawRule, val subject: FirBasedSymbol<*>? = null) : KotlinCondition
    data class Observer(
        val rule: ObserverRule,
        val member: org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol<*>? = null,
        val implementation: org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol<*>? = null,
    ) : KotlinCondition
    enum class ProofClause { ASSERTION, REFUTATION, LAW, PRECONDITION, POSTCONDITION, LOOP_INVARIANT }
    data class Proof(val clause: ProofClause, val declaration: FirBasedSymbol<*>? = null) : KotlinCondition
    enum class ReturnValue { TRUE, FALSE, NULL, NON_NULL, ANY }
    data class Returns(val effect: ReturnValue) : KotlinCondition
    data class Conditional(val effect: ReturnValue, val predicate: KotlinPredicate) : KotlinCondition
    enum class Collection { LIST, ARRAY }
    enum class Bound { NEGATIVE, NOT_BELOW_SIZE, ABOVE_SIZE }
    data class Index(val collection: Collection, val symbol: FirBasedSymbol<*>?, val bound: Bound) : KotlinCondition
    data class SubList(val symbol: FirBasedSymbol<*>?, val negative: Boolean) : KotlinCondition
    enum class OwnershipSite { UNFOLD, FOLD, CONSTRUCTION, HAVOC, LOOP_HEAD, PRECONDITION, POSTCONDITION }
    data class Ownership(val path: String, val site: OwnershipSite, val function: String? = null) : KotlinCondition
    enum class OverrideClause { PRECONDITION, POSTCONDITION }
    data class Override(val overridden: String, val clause: OverrideClause) : KotlinCondition
    enum class SkipReason {
        CONVERSION_ERRORS, UNIQUENESS_OR_LOCALITY_ERRORS, UNSUPPORTED_OWNERSHIP, INTERNAL_ERROR, MISSING_TERMINATION_MEASURE,
    }
    data class Skipped(val reason: SkipReason, val function: FirBasedSymbol<*>?) : KotlinCondition
    /** Evidence retains details independently of stable machine-readable reasons. */
    data class Translation(val reason: String, val detail: String) : KotlinCondition
    data class Tool(val reason: String, val detail: String) : KotlinCondition
    data object UntrackedWrite : KotlinCondition
    /** A recursive call that may not decrease the caller's termination measure. */
    data object Termination : KotlinCondition
    /** Backend obligations without Kotlin provenance remain explicitly unclassified. */
    data object Unclassified : KotlinCondition
}

data class RelatedLocation(val role: String, val source: KtSourceElement)
data class BackendEvidence(val errorId: String, val reasonId: String, val detail: String)

/** Kotlin origins identify cloned obligations; unattributed obligations retain backend node identity. */
@Suppress("UseDataClass")
class ObligationIdentity private constructor(private val location: Any, private val proposition: Any, private val origin: Any?) {
    constructor(location: Any, proposition: Any) : this(location, proposition, null)

    companion object {
        fun atOrigin(origin: Any): ObligationIdentity = ObligationIdentity(origin, origin, origin)
    }

    override fun equals(other: Any?): Boolean = other is ObligationIdentity &&
        if (origin != null || other.origin != null) origin == other.origin
        else location === other.location && proposition === other.proposition

    override fun hashCode(): Int = origin?.hashCode()
        ?: (31 * System.identityHashCode(location) + System.identityHashCode(proposition))
}

data class Failure(
    val kind: FailureKind,
    val primary: KtSourceElement?,
    val condition: KotlinCondition,
    val obligation: ObligationIdentity = ObligationIdentity(condition, condition),
    val related: List<RelatedLocation> = emptyList(),
    val counterexample: Nothing? = null,
    val backend: BackendEvidence? = null,
    val errorStyle: ErrorStyle = ErrorStyle.USER_FRIENDLY,
)
