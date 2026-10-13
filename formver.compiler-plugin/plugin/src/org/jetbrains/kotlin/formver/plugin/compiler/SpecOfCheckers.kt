/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.SpecOfRule
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.formver.common.externalFunctionId
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
import org.jetbrains.kotlin.formver.core.conversion.specificationResultMatches
import org.jetbrains.kotlin.formver.core.conversion.specOfSiblings
import org.jetbrains.kotlin.formver.core.conversion.specOfTargetName
import org.jetbrains.kotlin.formver.core.conversion.specOfTargets
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.core.isUnique

/** Reports a `@SpecOf` function that fails one of the checks below. */
class SpecOfDeclarationChecker(private val session: FirSession) : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirSimpleFunction) {
        val symbol = declaration.symbol
        val name = symbol.specOfTargetName(session) ?: return
        violation(declaration, name)?.let {
            reporter.reportFailure(Failure(FailureKind.INVALID_SPEC_OF, declaration.source, it))
        }
    }

    context(context: CheckerContext)
    private fun violation(declaration: FirSimpleFunction, name: String): KotlinCondition.SpecOf? {
        val symbol = declaration.symbol
        if (symbol.receiverParameterSymbol != null &&
            symbol.specOfTargets(session).none { it.getAnnotationByClassId(externalFunctionId, session) != null }) {
            return KotlinCondition.SpecOf(SpecOfRule.EXTENSION_RECEIVER)
        }
        if (!declaration.returnTypeRef.coneType.isUnit) return KotlinCondition.SpecOf(SpecOfRule.RETURN_TYPE)
        if (symbol.functionsDeclaredBeside(session).none { it.name.asString() == name }) {
            return KotlinCondition.SpecOf(SpecOfRule.MISSING_MEMBER, name)
        }
        val target = symbol.specOfTargets(session).singleOrNull()
            ?: return KotlinCondition.SpecOf(SpecOfRule.OVERLOAD, name)
        return targetViolation(declaration, target)
    }

    context(context: CheckerContext)
    @OptIn(SymbolInternals::class)
    private fun targetViolation(declaration: FirSimpleFunction, target: FirNamedFunctionSymbol): KotlinCondition.SpecOf? {
        val symbol = declaration.symbol
        val name = target.name.asString()
        if (target.specOfSiblings(session).size > 1) return KotlinCondition.SpecOf(SpecOfRule.MULTIPLE_SPECIFICATIONS, name)
        if (target.fir.ownSpecification() != null) return KotlinCondition.SpecOf(SpecOfRule.OWN_SPECIFICATION)
        val ownershipMatches = symbol.valueParameterSymbols.zip(target.valueParameterSymbols).all { (own, theirs) ->
            own.isUnique() == theirs.isUnique() && own.isBorrowed() == theirs.isBorrowed()
        }
        if (!ownershipMatches) return KotlinCondition.SpecOf(SpecOfRule.PARAMETER_OWNERSHIP)
        return bodyViolation(declaration, target)
    }

    private fun bodyViolation(declaration: FirSimpleFunction, target: FirNamedFunctionSymbol): KotlinCondition.SpecOf? {
        val statements = declaration.body?.statements.orEmpty()
        val pre = statements.getOrNull(0)?.extractFormverFirBlock { isFormverFunctionNamed("preconditions") }
        val post = statements.getOrNull(if (pre != null) 1 else 0)
            ?.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }
        if (statements.size != listOfNotNull(pre, post).size) {
            return KotlinCondition.SpecOf(SpecOfRule.BODY_SHAPE)
        }
        val resultType = declaration.body?.postconditionsResultType() ?: return null
        if (!declaration.symbol.specificationResultMatches(resultType, target, session)) {
            return KotlinCondition.SpecOf(SpecOfRule.POSTCONDITIONS_TYPE)
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
        reporter.reportFailure(Failure(FailureKind.INVALID_SPEC_OF, expression.source,
            KotlinCondition.SpecOf(SpecOfRule.CALL)))
    }
}
