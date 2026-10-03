/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.formver.core.conversion.extractFormverFirBlock
import org.jetbrains.kotlin.formver.core.conversion.functionsDeclaredBeside
import org.jetbrains.kotlin.formver.core.conversion.ownSpecification
import org.jetbrains.kotlin.formver.core.conversion.postconditionsResultType
import org.jetbrains.kotlin.formver.core.conversion.sameUpToAttributes
import org.jetbrains.kotlin.formver.core.conversion.specOfSiblings
import org.jetbrains.kotlin.formver.core.conversion.specOfTargetName
import org.jetbrains.kotlin.formver.core.conversion.specOfTargets
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.isUnique

/** Reports a `@SpecOf` function that fails one of the checks below. */
class SpecOfDeclarationChecker(private val session: FirSession) : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirSimpleFunction) {
        val symbol = declaration.symbol
        val name = symbol.specOfTargetName(session) ?: return
        violation(declaration, name)?.let { reporter.reportOn(declaration.source, PluginErrors.INVALID_SPEC_OF, it) }
    }

    context(context: CheckerContext)
    private fun violation(declaration: FirSimpleFunction, name: String): String? {
        val symbol = declaration.symbol
        if (symbol.receiverParameterSymbol != null) return "Invalid @SpecOf: extension receiver."
        if (!declaration.returnTypeRef.coneType.isUnit) return "Invalid @SpecOf: return type."
        if (symbol.functionsDeclaredBeside().none { it.name.asString() == name }) {
            return "Invalid @SpecOf: no member '$name'."
        }
        val target = symbol.specOfTargets(session).singleOrNull()
            ?: return "Invalid @SpecOf: no overload of '$name' with these parameter types."
        return targetViolation(declaration, target)
    }

    context(context: CheckerContext)
    @OptIn(SymbolInternals::class)
    private fun targetViolation(declaration: FirSimpleFunction, target: FirNamedFunctionSymbol): String? {
        val symbol = declaration.symbol
        val name = target.name.asString()
        if (target.specOfSiblings(session).size > 1) return "Invalid @SpecOf: several @SpecOf functions for '$name'."
        if (target.isPure(session)) return "Invalid @SpecOf: target is @Pure."
        if (target.fir.ownSpecification() != null) return "Invalid @SpecOf: target has its own specification."
        val ownershipMatches = symbol.valueParameterSymbols.zip(target.valueParameterSymbols).all { (own, theirs) ->
            own.isUnique() == theirs.isUnique() && own.isBorrowed() == theirs.isBorrowed()
        }
        if (!ownershipMatches) return "Invalid @SpecOf: parameter ownership."
        return bodyViolation(declaration, target)
    }

    private fun bodyViolation(declaration: FirSimpleFunction, target: FirNamedFunctionSymbol): String? {
        val statements = declaration.body?.statements.orEmpty()
        val pre = statements.getOrNull(0)?.extractFormverFirBlock { isFormverFunctionNamed("preconditions") }
        val post = statements.getOrNull(if (pre != null) 1 else 0)
            ?.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }
        if (statements.size != listOfNotNull(pre, post).size) {
            return "Invalid @SpecOf: body shape."
        }
        val resultType = declaration.body?.postconditionsResultType() ?: return null
        if (!resultType.sameUpToAttributes(target.resolvedReturnType)) {
            return "Invalid @SpecOf: postconditions type."
        }
        return null
    }
}

/** Reports a call to a `@SpecOf` function. */
class SpecOfCallChecker(private val session: FirSession) : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return
        if (callee.specOfTargetName(session) == null) return
        reporter.reportOn(expression.source, PluginErrors.INVALID_SPEC_OF, "Invalid @SpecOf: call.")
    }
}
