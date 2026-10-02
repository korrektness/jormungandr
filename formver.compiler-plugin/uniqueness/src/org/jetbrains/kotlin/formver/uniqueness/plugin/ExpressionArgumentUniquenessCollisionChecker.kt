package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_DUPLICATE_UNIQUE_ARGUMENT
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_OVERLAPPING_UNIQUE_ARGUMENTS

/**
 * Maps expressions that act as arguments to the uniqueness required by the corresponding parameter-like declaration.
 */
fun interface ArgumentUniquenessesMapper<Statement : FirStatement> {
    context(context: CheckerContext)
    fun mapArgumentUniquenessesOf(statement: Statement): List<Pair<FirExpression, Uniqueness>>
}

private fun Path.computeCommonPrefix(other: Path): Int {
    var commonPrefix = 0

    while (commonPrefix < size && commonPrefix < other.size) {
        if (this[commonPrefix] == other[commonPrefix]) {
            commonPrefix++
        } else {
            break
        }
    }

    return commonPrefix
}

/**
 * Whether the object at [longer] is reachable from the object at [shorter] through `@Unique` fields only, so that a
 * predicate held for one of them covers the other. [shorter] must share its first [commonPrefixLength] symbols with
 * [longer] and have no others.
 */
context(context: CheckerContext)
private fun extendsThroughUniqueFields(shorter: Path, longer: Path, commonPrefixLength: Int): Boolean =
    commonPrefixLength == shorter.size &&
            longer.subList(shorter.size, longer.size).resolveDeclaredUniqueness() == Uniqueness.Unique

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkUniquenessCollision(
    ownerElement: FirElement,
    leftArgument: FirExpression,
    rightArgument: FirExpression
) {
    for (leftPath in leftArgument.resolveAccessState().enumeratePaths()) {
        for (rightPath in rightArgument.resolveAccessState().enumeratePaths()) {
            val commonPrefixLength = leftPath.computeCommonPrefix(rightPath)

            if (commonPrefixLength == 0) continue

            val leftSource = leftArgument.source ?: ownerElement.source
            val rightSource = rightArgument.source ?: ownerElement.source

            if (commonPrefixLength == leftPath.size && commonPrefixLength == rightPath.size) {
                if (leftSource != rightSource) {
                    reporter.reportOn(leftSource, INVALID_DUPLICATE_UNIQUE_ARGUMENT, leftPath)
                }
                reporter.reportOn(rightSource, INVALID_DUPLICATE_UNIQUE_ARGUMENT, rightPath)
            } else if (
                extendsThroughUniqueFields(leftPath, rightPath, commonPrefixLength) ||
                extendsThroughUniqueFields(rightPath, leftPath, commonPrefixLength)
            ) {
                if (leftSource != rightSource) {
                    reporter.reportOn(leftSource, INVALID_OVERLAPPING_UNIQUE_ARGUMENTS, leftPath, rightPath)
                }
                reporter.reportOn(rightSource, INVALID_OVERLAPPING_UNIQUE_ARGUMENTS, rightPath, leftPath)
            }
        }
    }
}

/**
 * Checker for detecting collisions between the arguments of one expression when at least one of them is passed to a
 * unique parameter.
 *
 * Two arguments collide when their access paths are identical, or when one access path extends the other through
 * `@Unique` fields only. For example, passing `x` to a unique parameter and `x.f` for a `@Unique` field `f` to any
 * parameter is invalid: the callee would hold the predicate of one and a shared alias into it. Extending through a
 * field that is not `@Unique` reaches an object the predicate does not cover, so `x` and `x.value` for an `Int` field `value` do
 * not collide.
 *
 * @param Statement the FIR expression kind handled by this checker.
 * @param argumentUniquenessMapper the mapper for resolving required uniqueness of argument-like expressions.
 * @param shouldBeChecked the predicate deciding whether this checker handles a particular expression instance.
 */
class ExpressionArgumentUniquenessCollisionChecker<Statement : FirStatement>(
    private val argumentUniquenessMapper: ArgumentUniquenessesMapper<Statement>,
    private val shouldBeChecked: (Statement) -> Boolean = { true }
) : FirExpressionChecker<Statement>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: Statement) {
        if (!shouldBeChecked(expression)) return

        val argumentUniquenesses = argumentUniquenessMapper.mapArgumentUniquenessesOf(expression)

        for ((index, left) in argumentUniquenesses.withIndex()) {
            for (right in argumentUniquenesses.subList(index + 1, argumentUniquenesses.size)) {
                if (left.second != Uniqueness.Unique && right.second != Uniqueness.Unique) continue
                checkUniquenessCollision(expression, left.first, right.first)
            }
        }
    }
}

/**
 * Maps the dispatch receiver of [expression] to `Shared`: a dispatch receiver cannot be annotated.
 */
private fun dispatchReceiverUniquenessOf(expression: FirQualifiedAccessExpression): List<Pair<FirExpression, Uniqueness>> =
    listOfNotNull(expression.dispatchReceiver?.let { it to Uniqueness.Shared })

/**
 * Checks collisions between value arguments, receivers, and context arguments of [FirFunctionCall]s.
 */
val FunctionCallArgumentUniquenessCollisionChecker =
    ExpressionArgumentUniquenessCollisionChecker<FirFunctionCall>(
        { expression ->
            dispatchReceiverUniquenessOf(expression) +
                    QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(expression) +
                    CallArgumentUniquenessesMapper.mapArgumentTypeFactsOf(expression)
        }
    )

/**
 * Checks collisions between receivers and context arguments of qualified accesses that are not [FirFunctionCall]s.
 */
val QualifiedAccessArgumentUniquenessCollisionChecker =
    ExpressionArgumentUniquenessCollisionChecker<FirQualifiedAccessExpression>(
        { expression ->
            dispatchReceiverUniquenessOf(expression) +
                    QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(expression)
        },
        { statement -> statement !is FirFunctionCall }
    )
