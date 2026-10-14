/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.formver.common.FailureKind

object PluginErrors : KtDiagnosticsContainer() {
    val VIPER_TEXT by info2<PsiElement, String, String>(SourceElementPositioningStrategies.DECLARATION_NAME)
    val EXP_EMBEDDING by info2<PsiElement, String, String>(SourceElementPositioningStrategies.DECLARATION_NAME)
    val INTERNAL_ERROR get() = FailureKind.INTERNAL_ERROR
    val VERIFIER_UNAVAILABLE get() = FailureKind.VERIFIER_UNAVAILABLE
    val UNIQUENESS_CFG by info1<PsiElement, String>(SourceElementPositioningStrategies.DECLARATION_NAME)
    val INVALID_EXPOSES get() = FailureKind.INVALID_EXPOSES
    val INVALID_SPEC_OF get() = FailureKind.INVALID_SPEC_OF
    val INVALID_EXTERNAL_SPEC get() = FailureKind.INVALID_EXTERNAL_SPEC
    val EXTERNAL_SPEC_OF_VERIFIED_DECLARATION get() = FailureKind.EXTERNAL_SPEC_OF_VERIFIED_DECLARATION
    val INVALID_OBSERVER get() = FailureKind.INVALID_OBSERVER
    val INVALID_LAW get() = FailureKind.INVALID_LAW
    override fun getRendererFactory() = FormalVerificationPluginErrorMessages

    fun tags() = listOf(
        VIPER_TEXT.name,
        EXP_EMBEDDING.name,
        INTERNAL_ERROR.name,
        VERIFIER_UNAVAILABLE.name,
        UNIQUENESS_CFG.name,
        INVALID_EXPOSES.name,
        INVALID_SPEC_OF.name,
        INVALID_EXTERNAL_SPEC.name,
        EXTERNAL_SPEC_OF_VERIFIED_DECLARATION.name,
        INVALID_OBSERVER.name,
        INVALID_LAW.name,
    )
}


object VerificationErrors : KtDiagnosticsContainer() {
    val CONDITIONAL_EFFECT_ERROR get() = FailureKind.CONDITIONAL_EFFECT_ERROR.factory
    val VIPER_VERIFICATION_ERROR get() = FailureKind.VIPER_VERIFICATION_ERROR.factory
    val POSSIBLE_INDEX_OUT_OF_BOUND get() = FailureKind.POSSIBLE_INDEX_OUT_OF_BOUND.factory
    val UNEXPECTED_RETURNED_VALUE get() = FailureKind.UNEXPECTED_RETURNED_VALUE.factory
    val INVALID_SUBLIST_RANGE get() = FailureKind.INVALID_SUBLIST_RANGE.factory
    val OWNERSHIP_NOT_ESTABLISHED get() = FailureKind.OWNERSHIP_NOT_ESTABLISHED.factory
    val OVERRIDE_NOT_REFINING get() = FailureKind.OVERRIDE_NOT_REFINING.factory

    val INVALID_VIPER_PROGRAM get() = FailureKind.INVALID_VIPER_PROGRAM
    val VERIFICATION_TIMEOUT get() = FailureKind.VERIFICATION_TIMEOUT
    override fun getRendererFactory() = FormalVerificationPluginErrorMessages
    fun tags() = FailureKind.entries.filter { it.category == org.jetbrains.kotlin.formver.common.FailureCategory.PROOF }
        .map { it.factory.name } + INVALID_VIPER_PROGRAM.name + VERIFICATION_TIMEOUT.name
}
