package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.ast.info
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.plugin.standard.termination.FunctionTerminationError
import viper.silver.verifier.reasons

/** Converts backend evidence into a Kotlin obligation without constructing diagnostic text. */
fun VerificationError.toFailure(source: KtSourceElement?): Failure {
    val role = lookupSourceRole()
    val counterexample = kotlinCounterexample()
    val condition = when (role) {
        is SourceRole.Arithmetic -> role.condition
        is SourceRole.Proof -> KotlinCondition.Proof(
            role.clause, role.declaration, role.subject,
            position.unwrapOr<KtSourceElement> { null }?.takeUnless { it == source },
            if (role.clause == KotlinCondition.ProofClause.LOOP_INVARIANT) loopPhase() else null,
        )
        is SourceRole.ReturnsEffect -> KotlinCondition.Returns(role.toReturnValue())
        is SourceRole.ConditionalEffect -> KotlinCondition.Conditional(role.effect.toReturnValue(), role.condition.toPredicate())
        is SourceRole.ListElementAccessCheck -> KotlinCondition.Index(
            KotlinCondition.Collection.LIST, collectionSymbol(),
            when (role.accessType) {
                SourceRole.ListElementAccessCheck.AccessCheckType.LESS_THAN_ZERO -> KotlinCondition.Bound.NEGATIVE
                SourceRole.ListElementAccessCheck.AccessCheckType.GREATER_THAN_LIST_SIZE -> KotlinCondition.Bound.ABOVE_SIZE
            },
        )
        is SourceRole.ArrayElementAccessCheck -> KotlinCondition.Index(
            KotlinCondition.Collection.ARRAY, role.array,
            when (role.bound) {
                SourceRole.ArrayElementAccessCheck.Bound.NEGATIVE -> KotlinCondition.Bound.NEGATIVE
                SourceRole.ArrayElementAccessCheck.Bound.NOT_BELOW_SIZE -> KotlinCondition.Bound.NOT_BELOW_SIZE
            },
        )
        is SourceRole.SubListCreation -> KotlinCondition.SubList(collectionSymbol(), role is SourceRole.SubListCreation.CheckNegativeIndices)
        is SourceRole.Ownership -> role.toCondition()
        is SourceRole.OverrideRefinement -> KotlinCondition.Override(
            role.overridden, when (role.clause) {
                SourceRole.OverrideRefinement.Clause.PRECONDITION -> KotlinCondition.OverrideClause.PRECONDITION
                SourceRole.OverrideRefinement.Clause.POSTCONDITION -> KotlinCondition.OverrideClause.POSTCONDITION
            },
        )
        is SourceRole.ObserverAccess -> KotlinCondition.Ownership("the observer receiver", KotlinCondition.OwnershipSite.UNFOLD)
        else -> if (result is FunctionTerminationError) KotlinCondition.Termination else KotlinCondition.Unclassified
    }
    val kind = condition.failureKind()
    return Failure(
        kind, source, condition,
        if (source == null) ObligationIdentity(result.offendingNode(), result.reason().offendingNode())
        else ObligationIdentity.atOrigin(listOf(source, condition, obligationShape(result.offendingNode()),
            // The lower and upper bound of one range check are one obligation.
            if (role is SourceRole.Arithmetic) null else obligationShape(result.reason().offendingNode()))),
        related = (if (role is SourceRole.Proof) listOfNotNull(
            role.declaration?.source?.takeUnless { it == source }?.let { RelatedLocation("Kotlin clause declaration", it) },
            position.unwrapOr<KtSourceElement> { null }?.takeUnless { it == source }
                ?.let { RelatedLocation("Kotlin clause", it) },
        ) else emptyList()) + counterexample?.bindings.orEmpty().mapNotNull { binding ->
            binding.source?.takeUnless { it == source }?.let { RelatedLocation("Counterexample: ${binding.path}", it) }
        }.distinctBy { it.source },
        counterexample = counterexample,
        backend = BackendEvidence(id, result.reason().id(), msg),
    )
}

private fun VerificationError.loopPhase(): KotlinCondition.LoopPhase? = when (result) {
    is viper.silver.verifier.errors.LoopInvariantNotEstablished -> KotlinCondition.LoopPhase.ENTRY
    is viper.silver.verifier.errors.LoopInvariantNotPreserved -> KotlinCondition.LoopPhase.PRESERVATION
    // The invariant is asserted again where the loop exits.
    is viper.silver.verifier.errors.AssertFailed -> KotlinCondition.LoopPhase.EXIT
    else -> null
}

private fun KotlinCondition.failureKind(): FailureKind = when (this) {
    is KotlinCondition.Proof -> FailureKind.VERIFICATION_CONDITION_NOT_ESTABLISHED
    is KotlinCondition.Returns -> FailureKind.UNEXPECTED_RETURNED_VALUE
    is KotlinCondition.Conditional -> FailureKind.CONDITIONAL_EFFECT_ERROR
    is KotlinCondition.IntegerOverflow -> FailureKind.INTEGER_OVERFLOW
    is KotlinCondition.DivisionByZero -> FailureKind.DIVISION_BY_ZERO
    is KotlinCondition.Index -> FailureKind.POSSIBLE_INDEX_OUT_OF_BOUND
    is KotlinCondition.SubList -> FailureKind.INVALID_SUBLIST_RANGE
    is KotlinCondition.Ownership -> FailureKind.OWNERSHIP_NOT_ESTABLISHED
    is KotlinCondition.Override -> FailureKind.OVERRIDE_NOT_REFINING
    KotlinCondition.Termination -> FailureKind.TERMINATION_NOT_ESTABLISHED
    KotlinCondition.Unclassified -> FailureKind.VIPER_VERIFICATION_ERROR
    else -> error("A proof adapter produced a non-proof condition")
}

/** Backend products encode clause structure without diagnostic text; metadata is presentation-independent. */
private fun obligationShape(value: Any?): Any? = when (value) {
    is viper.silver.ast.Position, is viper.silver.ast.Info, is viper.silver.ast.ErrorTrafo -> Unit
    is scala.Product -> {
        val source = (value as? viper.silver.ast.Node)?.let {
            Position.fromSilver(it.prettyMetadata._1()).unwrapOr<KtSourceElement> { null }
        }
        val fields = mutableListOf<Any?>(value.productPrefix(), source)
        val iterator = value.productIterator()
        while (iterator.hasNext()) fields.add(obligationShape(iterator.next()))
        fields
    }
    is scala.collection.Iterable<*> -> {
        val elements = mutableListOf<Any?>()
        val iterator = value.iterator()
        while (iterator.hasNext()) elements.add(obligationShape(iterator.next()))
        elements
    }
    else -> value
}

private fun SourceRole.ReturnsEffect.toReturnValue(): KotlinCondition.ReturnValue = when (this) {
    is SourceRole.ReturnsEffect.Bool -> if (bool) KotlinCondition.ReturnValue.TRUE else KotlinCondition.ReturnValue.FALSE
    is SourceRole.ReturnsEffect.Null -> if (negated) KotlinCondition.ReturnValue.NON_NULL else KotlinCondition.ReturnValue.NULL
    SourceRole.ReturnsEffect.Wildcard -> KotlinCondition.ReturnValue.ANY
}

private fun SourceRole.Condition.toPredicate(): KotlinPredicate = when (this) {
    is SourceRole.FirSymbolHolder -> KotlinPredicate.Symbol(firSymbol)
    is SourceRole.Condition.Constant -> KotlinPredicate.Constant(literal)
    is SourceRole.Condition.IsNull -> KotlinPredicate.IsNull(targetVariable, negated)
    is SourceRole.Condition.IsType -> KotlinPredicate.IsType(targetVariable, expectedType, negated)
    is SourceRole.Condition.Negation -> KotlinPredicate.Negation(arg.toPredicate())
    is SourceRole.Condition.Conjunction -> KotlinPredicate.Conjunction(lhs.toPredicate(), rhs.toPredicate())
    is SourceRole.Condition.Disjunction -> KotlinPredicate.Disjunction(lhs.toPredicate(), rhs.toPredicate())
}

private fun SourceRole.Ownership.toCondition(): KotlinCondition.Ownership = when (val s = site) {
    SourceRole.Ownership.Site.Unfold -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.UNFOLD)
    SourceRole.Ownership.Site.Fold -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.FOLD)
    SourceRole.Ownership.Site.Construction -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.CONSTRUCTION)
    SourceRole.Ownership.Site.Havoc -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.HAVOC)
    SourceRole.Ownership.Site.LoopHead -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.LOOP_HEAD)
    is SourceRole.Ownership.Site.Precondition -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.PRECONDITION, s.function)
    is SourceRole.Ownership.Site.Postcondition -> KotlinCondition.Ownership(path, KotlinCondition.OwnershipSite.POSTCONDITION, s.function)
}

private fun VerificationError.collectionSymbol() =
    locationNode.asCallable().arg(0).info.unwrapOr<SourceRole.FirSymbolHolder> { null }?.firSymbol

private fun VerificationError.lookupSourceRole(): SourceRole? {
    fun applicable(role: SourceRole?): SourceRole? = when (role) {
        is SourceRole.Arithmetic, is SourceRole.Proof, is SourceRole.ReturnsEffect, is SourceRole.ConditionalEffect,
        is SourceRole.ListElementAccessCheck, is SourceRole.ArrayElementAccessCheck,
        is SourceRole.SubListCreation, is SourceRole.Ownership, is SourceRole.OverrideRefinement -> role
        is SourceRole.ObserverAccess -> role.takeIf { result.reason() is reasons.InsufficientPermission }
        is SourceRole.Condition, null -> null
    }
    val location = applicable(locationNode.getInfoOrNull<SourceRole>())
    // A termination failure's reason names the function whose measure does not decrease, not a proposition.
    val proposition = if (result.reason().offendingNode() !is viper.silver.ast.Exp) null
        else applicable(unverifiableProposition.getInfoOrNull<SourceRole>())
    return if (proposition is SourceRole.Arithmetic) proposition
        else if (location is SourceRole.Proof && proposition is SourceRole.ObserverAccess) proposition
        else location ?: proposition
}

/** Generated checks retain their source obligation when a transformed expression supplies the failing node. */
fun VerifierError.reportingPosition(checkedSource: KtSourceElement? = null): Position =
    when (val role = (this as? VerificationError)?.lookupSourceRole()) {
        is SourceRole.OverrideRefinement -> role.position
        is SourceRole.ObserverAccess -> role.position
        is SourceRole.Proof -> if (role.site.liesWithin(checkedSource)) role.site else {
            val postcondition = (this as VerificationError).result as? viper.silver.verifier.errors.PostconditionViolated
            if (postcondition != null) {
                val memberPosition = Position.fromSilver(postcondition.member().prettyMetadata._1())
                memberPosition.takeUnless { it == Position.NoPosition }
                    ?: checkedSource?.let { Position.Wrapped(it) } ?: position
            } else position
        }
        else -> position
    }

/**
 * Whether this position lies inside [declaration], or [declaration] is unknown. A body a class inherits is checked for
 * the class, so a position in the inherited body does not identify the failure.
 */
private fun Position.liesWithin(declaration: KtSourceElement?): Boolean {
    val source = unwrapOr<KtSourceElement> { null } ?: return false
    if (declaration == null) return true
    val sameFile = when {
        source is KtPsiSourceElement && declaration is KtPsiSourceElement -> source.psi.containingFile == declaration.psi.containingFile
        source is KtLightSourceElement && declaration is KtLightSourceElement -> source.treeStructure === declaration.treeStructure
        else -> false
    }
    return sameFile && declaration.startOffset <= source.startOffset && source.endOffset <= declaration.endOffset
}
