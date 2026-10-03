package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
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

/**
 * Reports a collision between the paths [leftPaths], read at [leftSource], and the paths [rightPaths], read at
 * [rightSource]: two identical paths, or one path that extends the other through `@Unique` fields only. A collision is
 * reported at [rightSource], and also at [leftSource] when [reportLeft] holds and the sources differ.
 */
context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkPathCollision(
    leftPaths: List<Path>,
    leftSource: KtSourceElement?,
    rightPaths: List<Path>,
    rightSource: KtSourceElement?,
    reportLeft: Boolean,
) {
    val alsoLeft = reportLeft && leftSource != rightSource
    for (leftPath in leftPaths) {
        for (rightPath in rightPaths) {
            val commonPrefixLength = leftPath.computeCommonPrefix(rightPath)

            if (commonPrefixLength == 0) continue

            if (commonPrefixLength == leftPath.size && commonPrefixLength == rightPath.size) {
                if (alsoLeft) reporter.reportOn(leftSource, INVALID_DUPLICATE_UNIQUE_ARGUMENT, leftPath)
                reporter.reportOn(rightSource, INVALID_DUPLICATE_UNIQUE_ARGUMENT, rightPath)
            } else if (
                extendsThroughUniqueFields(leftPath, rightPath, commonPrefixLength) ||
                extendsThroughUniqueFields(rightPath, leftPath, commonPrefixLength)
            ) {
                if (alsoLeft) reporter.reportOn(leftSource, INVALID_OVERLAPPING_UNIQUE_ARGUMENTS, leftPath, rightPath)
                reporter.reportOn(rightSource, INVALID_OVERLAPPING_UNIQUE_ARGUMENTS, rightPath, leftPath)
            }
        }
    }
}

context(context: CheckerContext)
private fun FirExpression.accessedPaths(): List<Path> = resolveAccessState().enumeratePaths().toList()

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
                checkPathCollision(
                    left.first.accessedPaths(),
                    left.first.source ?: expression.source,
                    right.first.accessedPaths(),
                    right.first.source ?: expression.source,
                    reportLeft = true,
                )
            }
        }
    }
}

/**
 * Checks collisions between value arguments, receivers, and context arguments of [FirFunctionCall]s.
 */
val FunctionCallArgumentUniquenessCollisionChecker =
    ExpressionArgumentUniquenessCollisionChecker<FirFunctionCall>(
        { expression ->
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
            QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(expression)
        },
        { statement -> statement !is FirFunctionCall }
    )

/**
 * Checks that no default value of a parameter of a [FirFunction] collides with another parameter or another default
 * value when at least one of them is unique. A call that evaluates the default passes the object it reads in both
 * places, as a call passing colliding arguments would.
 */
object DefaultArgumentUniquenessCollisionChecker : FirFunctionChecker(MppCheckerKind.Common) {
    private data class Operand(val paths: List<Path>, val source: KtSourceElement?, val uniqueness: Uniqueness, val isDefault: Boolean)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration.valueParameters.none { it.defaultValue != null }) return

        val operands = buildList {
            declaration.receiverParameter?.let {
                add(Operand(listOf(listOf(it.symbol)), it.source, it.symbol.resolveUniqueness(), isDefault = false))
            }
            for (parameter in declaration.contextParameters + declaration.valueParameters) {
                val uniqueness = parameter.symbol.resolveUniqueness()
                add(Operand(listOf(listOf(parameter.symbol)), parameter.source, uniqueness, isDefault = false))
                parameter.defaultValue?.let {
                    add(Operand(it.accessedPaths(), it.source ?: parameter.source, uniqueness, isDefault = true))
                }
            }
        }

        for ((index, left) in operands.withIndex()) {
            for (right in operands.subList(index + 1, operands.size)) {
                if (!left.isDefault && !right.isDefault) continue
                if (left.uniqueness != Uniqueness.Unique && right.uniqueness != Uniqueness.Unique) continue
                val (other, default) = if (right.isDefault) left to right else right to left
                checkPathCollision(other.paths, other.source, default.paths, default.source, reportLeft = other.isDefault)
            }
        }
    }
}
