/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationErrors
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.SourceRoleConditionPrettyPrinter.prettyPrint
import org.jetbrains.kotlin.formver.viper.ast.info
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr
import org.jetbrains.kotlin.formver.viper.errors.VerificationError

sealed interface FormattedError {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun report(source: KtSourceElement?)
}

class ReturnsEffectError(private val sourceRole: SourceRole.ReturnsEffect) : FormattedError {
    private val SourceRole.ReturnsEffect.asUserFriendlyMessage: String
        get() = when (this) {
            is SourceRole.ReturnsEffect.Bool -> if (bool) "false" else "true"
            is SourceRole.ReturnsEffect.Null -> if (negated) "null" else "non-null"
            else -> error("Unknown returns effect: $this")
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        reporter.reportOn(source, VerificationErrors.UNEXPECTED_RETURNED_VALUE, msg())
    }

    fun msg(): String = sourceRole.asUserFriendlyMessage
}

class ConditionalEffectError(private val sourceRole: SourceRole.ConditionalEffect) : FormattedError {
    private val SourceRole.ReturnsEffect.asUserFriendlyMessage: String
        get() = when (this) {
            is SourceRole.ReturnsEffect.Bool, is SourceRole.ReturnsEffect.Null -> "a $this value is returned"
            SourceRole.ReturnsEffect.Wildcard -> "the function returns"
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        val (returnEffectMsg, conditionPrettyPrinted) = msg()
        reporter.reportOn(source, VerificationErrors.CONDITIONAL_EFFECT_ERROR, returnEffectMsg, conditionPrettyPrinted)
    }

    fun msg(): Pair<String, String> = sourceRole.let { (returnEffect, condition) ->
        returnEffect.asUserFriendlyMessage to condition.prettyPrint()
    }
}

class DefaultError(private val error: VerificationError) : FormattedError {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        reporter.reportOn(source, VerificationErrors.VIPER_VERIFICATION_ERROR, msg())
    }

    fun msg(): String = error.msg
}

/**
 * An index that may be out of bounds. [target] names the indexed collection; when it is an inlined expression with no
 * symbol, it names no variable, since the compiler highlights the sub-expression causing the problem.
 */
class IndexOutOfBoundError(private val target: String, private val violation: String) : FormattedError {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        val (targetInfo, userFriendlyMessage) = msg()
        reporter.reportOn(
            source,
            VerificationErrors.POSSIBLE_INDEX_OUT_OF_BOUND,
            targetInfo,
            userFriendlyMessage,
        )
    }

    fun msg(): Pair<String, String> = target to violation
}

private fun VerificationError.listIndexOutOfBound(sourceRole: SourceRole.ListElementAccessCheck): IndexOutOfBoundError {
    val targetList = locationNode.asCallable().arg(0).info.unwrapOr<SourceRole.FirSymbolHolder> { null }
    val violation = when (sourceRole.accessType) {
        SourceRole.ListElementAccessCheck.AccessCheckType.LESS_THAN_ZERO -> "less than zero"
        SourceRole.ListElementAccessCheck.AccessCheckType.GREATER_THAN_LIST_SIZE -> "greater than the list's size"
    }
    return IndexOutOfBoundError(targetList.formatListMessage(), violation)
}

private fun SourceRole.ArrayElementAccessCheck.indexOutOfBound(): IndexOutOfBoundError {
    val target = array?.let { "array '${FirDiagnosticRenderers.DECLARATION_NAME.render(it)}'" }
        ?: "the following array sub-expression"
    val violation = when (bound) {
        SourceRole.ArrayElementAccessCheck.Bound.NEGATIVE -> "less than zero"
        SourceRole.ArrayElementAccessCheck.Bound.NOT_BELOW_SIZE -> "not less than the array's size"
    }
    return IndexOutOfBoundError(target, violation)
}

class InvalidSubListRangeError(
    private val error: VerificationError,
    private val sourceRole: SourceRole.SubListCreation
) : FormattedError {
    private val SourceRole.SubListCreation.asUserFriendlyMessage: String
        get() = when (this) {
            is SourceRole.SubListCreation.CheckNegativeIndices -> "including negative indices"
            is SourceRole.SubListCreation.CheckInSize -> "greater than the list's size"
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        val (targetInfo, userFriendlyMessage) = msg()
        reporter.reportOn(
            source,
            VerificationErrors.INVALID_SUBLIST_RANGE,
            targetInfo,
            userFriendlyMessage,
        )
    }

    fun msg(): Pair<String, String> {
        val targetListInfo = error.locationNode.asCallable().arg(0).info
        val targetList = targetListInfo.unwrapOr<SourceRole.FirSymbolHolder> { null }
        return targetList.formatListMessage() to sourceRole.asUserFriendlyMessage
    }
}

/** A permission on a path the fold state tracks that the verifier could not establish. */
class OwnershipError(private val sourceRole: SourceRole.Ownership) : FormattedError {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?) {
        val (path, site) = msg()
        reporter.reportOn(source, VerificationErrors.OWNERSHIP_NOT_ESTABLISHED, path, site)
    }

    fun msg(): Pair<String, String> = when (val site = sourceRole.site) {
        SourceRole.Ownership.Site.Unfold -> sourceRole.path to "to unfold it"
        SourceRole.Ownership.Site.Fold -> sourceRole.path to "to fold it"
        SourceRole.Ownership.Site.Havoc -> sourceRole.path to "to havoc it after a call"
        SourceRole.Ownership.Site.LoopHead -> sourceRole.path to "at the loop head"
        is SourceRole.Ownership.Site.Precondition ->
            "the argument for parameter ${sourceRole.path}" to "at the call to `${site.function}`"
        is SourceRole.Ownership.Site.Postcondition -> sourceRole.path to "when `${site.function}` returns"
    }
}

fun VerificationError.formatUserFriendly(): FormattedError? =
    when (val sourceRole = lookupSourceRole()) {
        is SourceRole.ReturnsEffect -> ReturnsEffectError(sourceRole)
        is SourceRole.ConditionalEffect -> ConditionalEffectError(sourceRole)
        is SourceRole.ListElementAccessCheck -> listIndexOutOfBound(sourceRole)
        is SourceRole.ArrayElementAccessCheck -> sourceRole.indexOutOfBound()
        is SourceRole.SubListCreation -> InvalidSubListRangeError(this, sourceRole)
        is SourceRole.Ownership -> OwnershipError(sourceRole)
        else -> null
    }

/**
 * Find the contained [SourceRole] within a verification error.
 * If the role is not found, then returns `null`.
 */
private fun VerificationError.lookupSourceRole(): SourceRole? {
    /**
     * Lookup strategy:
     * The source role can be embedded either in the error's location node, or in the fault proposition.
     *
     * As an example, `PreconditionInCallFalse` errors have as offending node result the call-site of the called method.
     * But the actual info we are interested in is on the pre-condition, contained in the reason's offending node.
     */
    return when (val locationNodeRole = locationNode.getInfoOrNull<SourceRole>()) {
        null -> unverifiableProposition.getInfoOrNull<SourceRole>()
        else -> locationNodeRole
    }
}

private fun SourceRole.FirSymbolHolder?.formatListMessage(): String = when (this) {
    null -> "the following list sub-expression"
    else -> {
        val listName = FirDiagnosticRenderers.DECLARATION_NAME.render(firSymbol)
        "list '${listName}'"
    }
}
