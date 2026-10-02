/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * The checker extensions that report uniqueness diagnostics.
 */
val uniquenessCheckerFactories: List<FirAdditionalCheckersExtension.Factory> = listOf(
    UniquenessAdditionalCheckers.getFactory(),
)

/**
 * Registers the uniqueness checker. The `@Unique` type attribute it reads is registered by
 * [org.jetbrains.kotlin.formver.locality.plugin.LocalityExtensionRegistrar], which must be registered too.
 */
class UniquenessExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        registerDiagnosticContainers(UniquenessErrors)
        +ExpressionAccessStateResolver.getFactory()
        +ExpressionUniquenessResolver.getFactory()
        +GraphUniquenessStatesResolver.getFactory()
        uniquenessCheckerFactories.forEach { +it }
        +UniquenessFacts.getFactory()
    }
}
