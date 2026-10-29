/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.cfa.FirControlFlowChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors

class PluginAdditionalCheckers(session: FirSession, config: PluginConfiguration) :
    FirAdditionalCheckersExtension(session) {
    companion object {
        fun getFactory(config: PluginConfiguration): Factory {
            return Factory { session -> PluginAdditionalCheckers(session, config) }
        }
    }

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOf(ExternalSpecificationBindingChecker)
        override val functionCheckers: Set<FirFunctionChecker> = if (config.verificationWorkers > 0) emptySet() else setOf(
            ViperPoweredDeclarationChecker(session, config, VerifierLocator(), ReportedVerifierErrors()),
        )
        override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = setOf(
            FunctionUniquenessStateRenderingChecker(config),
            SpecOfDeclarationChecker(session),
            LawDeclarationChecker,
            GhostObserverDeclarationChecker,
        )
        override val propertyCheckers: Set<FirPropertyChecker> = if (config.verificationWorkers > 0) emptySet() else setOf(
            ViperPoweredPropertyChecker(session, config, VerifierLocator(), ReportedVerifierErrors()),
        )
        override val fileCheckers: Set<FirFileChecker> = if (config.verificationWorkers > 0) {
            setOf(FullFileVerificationChecker(session, config))
        } else setOf(SpecVacuityChecker(session, config, VerifierLocator()))
        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOf(ObserverDeclarationChecker)
        override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(
            ObserverImplementationChecker,
            ExternalSpecificationImplementationChecker,
            LawImplementationChecker,
        ) + if (config.verificationWorkers > 0) emptySet() else setOf(
            ViperPoweredClassChecker(session, config, VerifierLocator(), ReportedVerifierErrors()),
        )
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> = setOf(ExposesPlacementChecker)
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(SpecOfCallChecker(session), ExposesCallChecker)
    }
}
