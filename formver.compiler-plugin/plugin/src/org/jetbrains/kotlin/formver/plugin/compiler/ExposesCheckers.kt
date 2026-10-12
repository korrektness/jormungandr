/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
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
        val owner = symbol.getContainingClassSymbol() as? FirRegularClassSymbol
        val placeholder = declaration.body?.statements?.singleOrNull()?.unwrappedResult() as? FirFunctionCall
        val validBody = (placeholder?.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)
            ?.isFormverFunctionNamed("ghostObserver") == true
        val validOwner = owner?.fir?.isInterface == true && owner.fir.status.isFun
        val hasValueOrTypeParameters = declaration.valueParameters.isNotEmpty() || declaration.typeParameters.isNotEmpty()
        val hasReceiverParameters = declaration.receiverParameter != null || declaration.contextParameters.isNotEmpty()
        val failure = when {
            !validOwner -> "Ghost observer outside a fun interface."
            !symbol.isPure(context.session) -> "Missing @Pure on ghost observer."
            declaration.status.isSuspend -> "Suspend ghost observer."
            symbol.isLaw(context.session) -> "Ghost observer marked @Law."
            hasValueOrTypeParameters || hasReceiverParameters -> "Ghost observer parameters."
            !validBody -> "Non-placeholder ghost observer body."
            else -> null
        }
        if (failure != null) reporter.reportOn(declaration.source, PluginErrors.INVALID_OBSERVER, failure)
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
                reporter.reportOn(expression.source, PluginErrors.INVALID_OBSERVER, "Ghost observer reference.")
            }
            if (callee?.isFormverFunctionNamed("exposes") == true) {
                reporter.reportOn(expression.source, PluginErrors.INVALID_EXPOSES, "Observer binding reference.")
            }
        }
        val (spec, lambda) = expression.samLambda() ?: return
        if (spec.ghostObservers().isEmpty()) return
        if (lambda.body?.statements?.firstOrNull()?.unwrappedResult()?.isExposes() != true) {
            reporter.reportOn(expression.source, PluginErrors.INVALID_EXPOSES, "Missing observer bindings.")
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
                reporter.reportOn(expression.source, PluginErrors.INVALID_OBSERVER, "Ghost placeholder use.")
            }
        }
        if (callee.isGhostObserver(context.session) && !inSpecification(expression, callee)) {
            reporter.reportOn(expression.source, PluginErrors.INVALID_OBSERVER, "Executable ghost observer use.")
        }
        if (!callee.isFormverFunctionNamed("exposes")) return
        val failure = violation(expression)
        if (failure != null) reporter.reportOn(expression.source, PluginErrors.INVALID_EXPOSES, failure)
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
    private fun violation(call: FirFunctionCall): String? {
        val lambda = context.containingElements.filterIsInstance<FirAnonymousFunction>().lastOrNull()
            ?: return "Observer bindings outside a SAM lambda."
        if (lambda.body?.statements?.firstOrNull()?.unwrappedResult() !== call) return "Misplaced observer bindings."
        val sam = context.containingElements.filterIsInstance<FirStatement>()
            .mapNotNull { it.samLambda() }.lastOrNull { it.lambda === lambda }
            ?: return "Observer bindings outside a SAM conversion."
        val spec = sam.spec
        if (!spec.fir.isInterface || !spec.fir.status.isFun) return "Invalid callable specification."
        return argumentsViolation(call, spec)
    }

    context(context: CheckerContext)
    private fun argumentsViolation(call: FirFunctionCall, spec: FirRegularClassSymbol): String? {
        val arguments = call.resolvedArgumentMapping.orEmpty().entries.associate { (argument, parameter) ->
            parameter.name.asString() to argument.unwrapArgument()
        }
        val token = arguments["spec"] as? FirGetClassCall ?: return "Invalid specification token."
        if (token.argument !is FirResolvedQualifier && token.argument !is FirClassReferenceExpression) {
            return "Non-literal specification token."
        }
        val namedSpec = token.argument.resolvedType.toRegularClassSymbol(context.session)
        if (namedSpec != spec) return "Mismatched callable specification."
        val body = (arguments["body"] as? FirAnonymousFunctionExpression)?.anonymousFunction
            ?: return "Non-literal observer bindings."
        if (!body.isLambda) return "Non-lambda observer bindings."
        return bindingsViolation(body, spec.ghostObservers())
    }

    context(context: CheckerContext)
    private fun bindingsViolation(body: FirAnonymousFunction, expected: Set<FirNamedFunctionSymbol>): String? {
        val bound = mutableSetOf<FirNamedFunctionSymbol>()
        val statements = body.body?.statements.orEmpty()
        var failure: String? = if (statements.isEmpty()) "Empty observer bindings." else null
        for (statement in statements) {
            val bindingFailure = bindingViolation(statement, body, expected, bound)
            failure = failure ?: bindingFailure
        }
        if (failure == null && bound != expected) failure = "Incomplete observer bindings."
        return failure
    }

    context(context: CheckerContext)
    private fun bindingViolation(
        statement: FirStatement,
        body: FirAnonymousFunction,
        expected: Set<FirNamedFunctionSymbol>,
        bound: MutableSet<FirNamedFunctionSymbol>,
    ): String? {
        val explicitReturn = statement is FirReturnExpression && statement.source?.kind == KtRealSourceElementKind
        val equality = statement.unwrappedResult() as? FirEqualityOperatorCall
        val left = equality?.argumentList?.arguments?.firstOrNull() as? FirFunctionCall
        val observer = (left?.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)?.unwrapFakeOverrides()
        val right = equality?.argumentList?.arguments?.getOrNull(1)
        return when {
            explicitReturn || equality == null -> "Invalid observer binding."
            equality.operation != FirOperation.EQ -> "Invalid observer binding operator."
            left == null -> "Invalid binding target."
            observer !in expected || !left.isBindingTarget(body) -> "Foreign observer binding."
            right == null || !right.isObserverBindingValue(context.session) -> "Invalid observer binding value."
            !bound.add(observer!!) -> "Duplicate observer binding."
            else -> null
        }
    }

    private fun FirFunctionCall.isBindingTarget(body: FirAnonymousFunction): Boolean {
        val receiver = dispatchReceiver as? FirThisReceiverExpression
        return argumentList.arguments.isEmpty() && receiver?.calleeReference?.boundSymbol == body.receiverParameter?.symbol
    }
}
