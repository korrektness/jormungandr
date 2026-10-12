/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.ObserverRule
import org.jetbrains.kotlin.formver.common.reportObserver
import org.jetbrains.kotlin.formver.common.ExposesRule
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.uniqueness.plugin.ghostObserverViolation
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.core.isGhostObserver
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.readonly.plugin.isInSpecification

private fun FirStatement.unwrappedResult(): FirStatement =
    if (this is FirReturnExpression) result else this

private fun FirStatement.isExposes(): Boolean =
    (this as? FirFunctionCall)?.toResolvedCallableSymbol()?.let {
        (it as? FirNamedFunctionSymbol)?.isFormverFunctionNamed("exposes")
    } == true

object GhostObserverDeclarationChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(SymbolInternals::class)
    override fun check(declaration: FirSimpleFunction) {
        val symbol = declaration.symbol
        if (!symbol.isGhostObserver(context.session)) return
        // Override validity belongs to ObserverDeclarationChecker.
        if (declaration.overriddenGhostObserver() != null) return
        val failure = declaration.ghostObserverViolation()
        if (failure != null) reporter.reportObserver(declaration.source, KotlinCondition.Observer(failure))
    }
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.ghostObservers(): Set<FirNamedFunctionSymbol> {
    val result = mutableSetOf<FirNamedFunctionSymbol>()
    val scope = unsubstitutedScope()
    for (name in scope.getCallableNames()) scope.processFunctionsByName(name) {
        val original = it.unwrapFakeOverrides()
        if (original.isGhostObserver(context.session)) result.add(original)
    }
    return result
}

private data class SamLambda(val spec: FirRegularClassSymbol, val lambda: FirAnonymousFunction)

context(context: CheckerContext)
private fun FirStatement.samLambda(): SamLambda? {
    val argument = when (this) {
        is FirSamConversionExpression -> expression
        is FirFunctionCall -> {
            if (toResolvedCallableSymbol()?.origin != FirDeclarationOrigin.SamConstructor) return null
            argumentList.arguments.singleOrNull()
        }
        else -> return null
    }
    val lambda = (argument?.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction ?: return null
    if (!lambda.isLambda) return null
    val spec = (this as FirExpression).resolvedType.toRegularClassSymbol(context.session) ?: return null
    return SamLambda(spec, lambda)
}

object ExposesPlacementChecker : FirBasicExpressionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirStatement) {
        if (expression is FirCallableReferenceAccess) {
            val callee = expression.toResolvedCallableSymbol() as? FirNamedFunctionSymbol
            if (callee?.isGhostObserver(context.session) == true || callee?.isFormverFunctionNamed("ghostObserver") == true) {
                reporter.reportObserver(expression.source, KotlinCondition.Observer(ObserverRule.GHOST_REFERENCE, callee))
            }
            if (callee?.isFormverFunctionNamed("exposes") == true) {
                reporter.reportFailure(Failure(FailureKind.INVALID_EXPOSES, expression.source, KotlinCondition.Exposes(ExposesRule.REFERENCE)))
            }
        }
        val (spec, lambda) = expression.samLambda() ?: return
        if (spec.ghostObservers().isEmpty()) return
        if (lambda.body?.statements?.firstOrNull()?.unwrappedResult()?.isExposes() != true) {
            reporter.reportFailure(Failure(FailureKind.INVALID_EXPOSES, expression.source, KotlinCondition.Exposes(ExposesRule.MISSING_BINDINGS)))
        }
    }
}

@OptIn(SymbolInternals::class)
object ExposesCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return
        if (callee.isFormverFunctionNamed("ghostObserver")) {
            val owner = context.containingDeclarations.lastOrNull() as? FirNamedFunctionSymbol
            if (owner?.isGhostObserver(context.session) != true) {
                reporter.reportObserver(expression.source, KotlinCondition.Observer(ObserverRule.GHOST_PLACEHOLDER_USE, callee))
            }
        }
        if (callee.isGhostObserver(context.session) && !inSpecification(expression, callee)) {
            reporter.reportObserver(expression.source, KotlinCondition.Observer(ObserverRule.EXECUTABLE_GHOST_USE, callee))
        }
        if (!callee.isFormverFunctionNamed("exposes")) return
        val failure = violation(expression)
        if (failure != null) reporter.reportFailure(Failure(FailureKind.INVALID_EXPOSES, expression.source, KotlinCondition.Exposes(failure)))
    }

    context(context: CheckerContext)
    private fun inSpecification(call: FirFunctionCall, observer: FirNamedFunctionSymbol): Boolean =
        context.isInSpecification() || isSupportedInterfaceLawCall(call, observer)

    context(context: CheckerContext)
    private fun isSupportedInterfaceLawCall(call: FirFunctionCall, observer: FirNamedFunctionSymbol): Boolean {
        val law = context.containingDeclarations.lastOrNull() as? FirNamedFunctionSymbol ?: return false
        if (!law.isLaw(context.session) || !law.isPure(context.session)) return false
        val owner = law.getContainingClassSymbol() as? FirRegularClassSymbol ?: return false
        val receiver = call.dispatchReceiver as? FirThisReceiverExpression
        return owner.fir.isInterface && receiver?.calleeReference?.boundSymbol == owner &&
            observer.unwrapFakeOverrides() in owner.ghostObservers()
    }

    context(context: CheckerContext)
    private fun violation(call: FirFunctionCall): ExposesRule? {
        val lambda = context.containingElements.filterIsInstance<FirAnonymousFunction>().lastOrNull()
            ?: return ExposesRule.OUTSIDE_SAM_LAMBDA
        if (lambda.body?.statements?.firstOrNull()?.unwrappedResult() !== call) return ExposesRule.PLACEMENT
        val sam = context.containingElements.filterIsInstance<FirStatement>()
            .mapNotNull { it.samLambda() }.lastOrNull { it.lambda === lambda }
            ?: return ExposesRule.OUTSIDE_SAM_CONVERSION
        val spec = sam.spec
        if (!spec.fir.isInterface || !spec.fir.status.isFun) return ExposesRule.CALLABLE_SPECIFICATION
        return argumentsViolation(call, spec, lambda)
    }

    context(context: CheckerContext)
    private fun argumentsViolation(call: FirFunctionCall, spec: FirRegularClassSymbol, lambda: FirAnonymousFunction): ExposesRule? {
        val arguments = call.resolvedArgumentMapping.orEmpty().entries.associate { (argument, parameter) ->
            parameter.name.asString() to argument.unwrapArgument()
        }
        val token = arguments["spec"] as? FirGetClassCall ?: return ExposesRule.SPECIFICATION_TOKEN
        if (token.argument !is FirResolvedQualifier && token.argument !is FirClassReferenceExpression) {
            return ExposesRule.NON_LITERAL_TOKEN
        }
        val namedSpec = token.argument.resolvedType.toRegularClassSymbol(context.session)
        if (namedSpec != spec) return ExposesRule.MISMATCHED_SPECIFICATION
        val body = (arguments["body"] as? FirAnonymousFunctionExpression)?.anonymousFunction
            ?: return ExposesRule.NON_LITERAL_BINDINGS
        if (!body.isLambda) return ExposesRule.NON_LAMBDA_BINDINGS
        return bindingsViolation(body, spec.ghostObservers(), lambda.valueParameters.map { it.symbol }.toSet())
    }

    context(context: CheckerContext)
    private fun bindingsViolation(
        body: FirAnonymousFunction,
        expected: Set<FirNamedFunctionSymbol>,
        inputs: Set<FirBasedSymbol<*>>,
    ): ExposesRule? {
        val bound = mutableSetOf<FirNamedFunctionSymbol>()
        val statements = body.body?.statements.orEmpty()
        var failure: ExposesRule? = if (statements.isEmpty()) ExposesRule.EMPTY_BINDINGS else null
        for (statement in statements) {
            val bindingFailure = bindingViolation(statement, body, expected, inputs, bound)
            failure = failure ?: bindingFailure
        }
        if (failure == null && bound != expected) failure = ExposesRule.INCOMPLETE_BINDINGS
        return failure
    }

    context(context: CheckerContext)
    private fun bindingViolation(
        statement: FirStatement,
        body: FirAnonymousFunction,
        expected: Set<FirNamedFunctionSymbol>,
        inputs: Set<FirBasedSymbol<*>>,
        bound: MutableSet<FirNamedFunctionSymbol>,
    ): ExposesRule? {
        val explicitReturn = statement is FirReturnExpression && statement.source?.kind == KtRealSourceElementKind
        val equality = statement.unwrappedResult() as? FirEqualityOperatorCall
        val left = equality?.argumentList?.arguments?.firstOrNull() as? FirFunctionCall
        val observer = (left?.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)?.unwrapFakeOverrides()
        val right = equality?.argumentList?.arguments?.getOrNull(1)
        return when {
            explicitReturn || equality == null -> ExposesRule.BINDING_SHAPE
            equality.operation != FirOperation.EQ -> ExposesRule.BINDING_OPERATOR
            left == null -> ExposesRule.BINDING_TARGET
            observer !in expected || !left.isBindingTarget(body) -> ExposesRule.FOREIGN_BINDING
            right == null || !right.isObserverBindingValue(context.session, body.receiverParameter?.symbol) -> ExposesRule.BINDING_VALUE
            right.readsAny(inputs) -> ExposesRule.PARAMETER_DEPENDENT_VALUE
            !bound.add(observer!!) -> ExposesRule.DUPLICATE_BINDING
            else -> null
        }
    }

    /** A binding is evaluated when the lambda is created, before any of [inputs] has a value. */
    private fun FirExpression.readsAny(inputs: Set<FirBasedSymbol<*>>): Boolean {
        var reads = false
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                val symbol: FirBasedSymbol<*>? = (element as? FirQualifiedAccessExpression)?.toResolvedCallableSymbol()
                if (symbol in inputs) reads = true
                element.acceptChildren(this)
            }
        })
        return reads
    }

    private fun FirFunctionCall.isBindingTarget(body: FirAnonymousFunction): Boolean {
        val receiver = dispatchReceiver as? FirThisReceiverExpression
        return argumentList.arguments.isEmpty() && receiver?.calleeReference?.boundSymbol == body.receiverParameter?.symbol
    }
}
