/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.formver.locality.contract.plugin.ExpressionLocalityContractResolver
import org.jetbrains.kotlin.formver.locality.contract.plugin.LocalityContractAdditionalCheckers
import org.jetbrains.kotlin.formver.locality.contract.plugin.LocalityContractErrors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val defaultLocalityAnnotationId =
    ClassId(
        FqName("org.jetbrains.kotlin.formver.plugin"),
        Name.identifier("Borrowed")
    )

/**
 * The checker extensions that report locality diagnostics.
 */
val localityCheckerFactories: List<FirAdditionalCheckersExtension.Factory> = listOf(
    LocalityAdditionalCheckers.getFactory(),
    LocalityContractAdditionalCheckers.getFactory(),
)

class LocalityExtensionRegistrar(
    private val localityAnnotationId: ClassId = defaultLocalityAnnotationId
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        registerDiagnosticContainers(LocalityErrors)
        registerDiagnosticContainers(LocalityContractErrors)
        +LocalityAttributeExtension.getFactory(localityAnnotationId)
        +ExpressionLocalityContractResolver.getFactory()
        +ExpressionLocalityResolver.getFactory()
        +GraphDeclaredSymbolsResolver.getFactory()
        +GraphCapturedSymbolsResolver.getFactory()
        +GraphScopeLocalityResolver.getFactory()
        localityCheckerFactories.forEach { +it }
    }
}
