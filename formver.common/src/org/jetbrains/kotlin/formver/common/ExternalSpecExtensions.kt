/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.contains
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

object ExternalSpecErrors : KtDiagnosticsContainer() {
    val EXTERNAL_SPEC_REFERENCE by error1<PsiElement, String>()

    override fun getRendererFactory() = ExternalSpecErrorMessages
}

object ExternalSpecErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("ExternalSpec") { map ->
        map.put(
            ExternalSpecErrors.EXTERNAL_SPEC_REFERENCE,
            "''{0}'' is declared in an external specification source, which is not compiled.",
            CommonRenderers.STRING,
        )
    }
}

/** Rejects references from compiled code to declarations of [specs], which have no compiled form. */
class ExternalSpecExtensionRegistrar(private val specs: ExternalSpecSources) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        registerDiagnosticContainers(ExternalSpecErrors)
        +FirAdditionalCheckersExtension.Factory { session -> ExternalSpecCheckers(session, specs) }
    }
}

private class ExternalSpecCheckers(session: FirSession, specs: ExternalSpecSources) :
    FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> = setOf(ReferenceChecker(specs))
    }

    override val typeCheckers: TypeCheckers = object : TypeCheckers() {
        override val resolvedTypeRefCheckers: Set<FirTypeChecker<FirResolvedTypeRef>> = setOf(TypeChecker(specs))
    }
}

private class ReferenceChecker(private val specs: ExternalSpecSources) : FirBasicExpressionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirStatement) {
        if (specs.isExternalSpec(context.containingFilePath)) return
        val symbol = when (expression) {
            is FirQualifiedAccessExpression -> expression.toResolvedCallableSymbol()
            is FirResolvedQualifier -> expression.symbol
            else -> null
        // Unresolved references are already compiler errors.
        } ?: return
        if (specs.declares(symbol)) {
            val name = when (symbol) {
                is FirCallableSymbol<*> -> symbol.callableId?.asSingleFqName()?.asString() ?: symbol.toString()
                is FirClassLikeSymbol<*> -> symbol.classId.asSingleFqName().asString()
                else -> symbol.toString()
            }
            reporter.reportOn(expression.source, ExternalSpecErrors.EXTERNAL_SPEC_REFERENCE, name)
        }
    }
}

private class TypeChecker(private val specs: ExternalSpecSources) :
    FirTypeChecker<FirResolvedTypeRef>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: FirResolvedTypeRef) {
        // Inferred types repeat a type that an explicit reference already introduced.
        if (typeRef.source == null || typeRef.source?.kind is KtFakeSourceElementKind) return
        if (specs.isExternalSpec(context.containingFilePath)) return
        val session = context.session
        var found: FirClassLikeSymbol<*>? = null
        typeRef.coneType.contains { type ->
            // An unresolved type is already a compiler error.
            val symbol = (type as? ConeClassLikeType)?.lookupTag?.toSymbol()
            if (symbol != null && specs.declares(symbol)) found = symbol
            found != null
        }
        found?.let { reporter.reportOn(typeRef.source, ExternalSpecErrors.EXTERNAL_SPEC_REFERENCE, it.classId.asSingleFqName().asString()) }
    }
}

private fun ExternalSpecSources.declares(symbol: FirBasedSymbol<*>): Boolean {
    val declaration = (symbol as? FirCallableSymbol<*>)?.unwrapFakeOverrides() ?: symbol
    if (declaration.origin != FirDeclarationOrigin.Source) return false
    val file = when (declaration) {
        is FirClassLikeSymbol<*> -> declaration.moduleData.session.firProvider.getFirClassifierContainerFileIfAny(declaration)
        is FirCallableSymbol<*> -> declaration.moduleData.session.firProvider.getFirCallableContainerFile(declaration)
        else -> null
    // Local declarations have no container file, and are never visible outside their own file.
    } ?: return false
    return isExternalSpec(file)
}

/** Drops the files of [specs] from the module before code generation. */
class ExternalSpecIrStripper(private val specs: ExternalSpecSources) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.removeAll { specs.isExternalSpec(it.fileEntry.name) }
    }
}
