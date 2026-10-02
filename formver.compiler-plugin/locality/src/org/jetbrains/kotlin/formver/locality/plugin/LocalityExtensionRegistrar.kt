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
import org.jetbrains.kotlin.formver.uniqueness.attribute.UniquenessAttributeExtension
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val defaultLocalityAnnotationId =
    ClassId(
        FqName("org.jetbrains.kotlin.formver.plugin"),
        Name.identifier("Borrowed")
    )

private val defaultUniquenessAnnotationId =
    ClassId(
        FqName("org.jetbrains.kotlin.formver.plugin"),
        Name.identifier("Unique")
    )

/**
 * The checker extensions that report locality diagnostics.
 */
val localityCheckerFactories: List<FirAdditionalCheckersExtension.Factory> = listOf(
    LocalityAdditionalCheckers.getFactory(),
    LocalityContractAdditionalCheckers.getFactory(),
)

/**
 * Registers the locality checker, and the `@Borrowed` and `@Unique` type attributes. Locality reads `@Unique` to find
 * the parameters that read-only callees borrow.
 */
class LocalityExtensionRegistrar(
    private val localityAnnotationId: ClassId = defaultLocalityAnnotationId,
    private val uniquenessAnnotationId: ClassId = defaultUniquenessAnnotationId,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        registerDiagnosticContainers(LocalityErrors)
        registerDiagnosticContainers(LocalityContractErrors)
        +LocalityAttributeExtension.getFactory(localityAnnotationId)
        +UniquenessAttributeExtension.getFactory(uniquenessAnnotationId)
        +ExpressionLocalityContractResolver.getFactory()
        +ExpressionLocalityResolver.getFactory()
        +GraphDeclaredSymbolsResolver.getFactory()
        +GraphCapturedSymbolsResolver.getFactory()
        +GraphScopeLocalityResolver.getFactory()
        localityCheckerFactories.forEach { +it }
    }
}
