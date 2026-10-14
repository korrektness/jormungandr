/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.formver.common.TrustedAssumption
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.LogLevel
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.TargetsSelection
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.core.conversion.InheritedImplementationObligation
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.constructionFailures
import org.jetbrains.kotlin.formver.core.conversion.hasSuperclassLaws
import org.jetbrains.kotlin.formver.core.conversion.inheritedImplementationObligations
import org.jetbrains.kotlin.formver.core.conversion.interfaceLaws
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.conversion.registerClassLaws
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.ast.registerAllNames

/** What a concrete class must prove. */
data class ClassObligations(
    /** The constructors whose bodies the class program verifies. */
    val constructors: List<FirConstructorSymbol>,
    val inherited: List<InheritedImplementationObligation>,
    /** Whether a constructor is `@NeverVerify`, so the class program does not verify the whole construction. */
    val skipsConstructor: Boolean,
)

/** A converted class program. */
data class ClassProgram(
    val program: viper.silver.ast.Program,
    /** Whether to verify [program]. */
    val verify: Boolean,
    /** Whether [program] proves the class's constructors and nothing skips its verification. */
    val verifiesConstruction: Boolean,
    val trustedAssumptions: Set<TrustedAssumption>,
)

/** Converts the Viper program that checks a concrete class. */
class ClassPrograms(private val session: FirSession, private val config: PluginConfiguration) {
    /** Whether [declaration], in the file at [path], is not checked at all. */
    fun isSkipped(declaration: FirRegularClass, path: String?): Boolean =
        session.externalSpecifications?.errors?.isNotEmpty() == true ||
            config.externalSpecs.isExternalSpec(path) ||
            declaration.origin != FirDeclarationOrigin.Source

    /**
     * What a concrete class must prove, or null when it has nothing to prove, its laws cannot be constructed, or it is
     * `@NeverVerify` or `@NeverConvert`.
     */
    context(context: CheckerContext)
    @OptIn(DirectDeclarationsAccess::class)
    fun implementationObligations(declaration: FirRegularClass): ClassObligations? {
        if (declaration.classKind.isInterface || declaration.isAbstract) return null
        if (config.conversionSelection in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE)) return null
        val symbol = declaration.symbol
        if (symbol.neverVerify(session)) return null
        val hasClassLaws = symbol.ownClassLaws(session).isNotEmpty() || symbol.hasSuperclassLaws(session)
        val inherited = symbol.inheritedImplementationObligations()
        val constructionFailed = constructionFailures(symbol, session).isNotEmpty()
        // Enum constructors run only for the entries, during class initialization.
        val allConstructors = if (constructionFailed || declaration.classKind == ClassKind.ENUM_CLASS) emptyList()
            else symbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>()
        val constructors = allConstructors.filterNot { it.neverVerify(session) }
        val hasLawHarness = symbol.interfaceLaws().isNotEmpty() || symbol.declarationSymbols.any { it.isLaw(session) }
        return when {
            constructors.isEmpty() && inherited.isEmpty() && !hasLawHarness -> null
            hasClassLaws && constructionFailed -> null
            else -> ClassObligations(constructors, inherited, skipsConstructor = constructors.size < allConstructors.size)
        }
    }

    /** The checked Viper program for [obligations], or null after a conversion error. Adds its Viper text to [dumps]. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun convert(declaration: FirRegularClass, obligations: ClassObligations, dumps: MutableList<String>): ClassProgram? {
        val converter = ProgramConverter(session, config, context, reporter)
        val provesConstruction = obligations.constructors.isNotEmpty() && !obligations.skipsConstructor
        if (provesConstruction) converter.provenConstruction = declaration.symbol
        converter.registerClassLaws(declaration.symbol)
        obligations.constructors.forEach { converter.registerConstructor(it) }
        for (obligation in obligations.inherited) {
            converter.registerInheritedImplementation(declaration.symbol, obligation.implementation, obligation.contracts)
        }
        converter.validateAll()
        if (converter.hadConversionError) return null
        converter.linearizeAll()
        val program = converter.buildProgram()
        with(converter.nameResolver) { program.registerAllNames() }
        converter.nameResolver.resolve()
        val dump = when (config.logLevel) {
            LogLevel.ONLY_WARNINGS -> null
            LogLevel.SHORT_VIPER_DUMP -> program.toShort().withoutPredicates()
            LogLevel.SHORT_VIPER_DUMP_WITH_PREDICATES -> program.toShort()
            LogLevel.FULL_VIPER_DUMP -> program
        }
        dump?.let { dumps += with(converter.nameResolver) { it.toDebugOutput() } }
        val viperProgram = with(converter.nameResolver) { program.toSilver() }
        SiliconFrontend.checkConsistency(viperProgram)
        val verify = config.verificationSelection !in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE) &&
            !converter.skipsVerification
        return ClassProgram(viperProgram, verify, provesConstruction && !converter.skipsVerification, converter.trust.assumptions)
    }

    /**
     * Whether the class program of [owner] verifies its construction, by a conversion whose diagnostics are discarded.
     * A failed conversion counts as no verification; the class checker reports why when it converts the class itself.
     */
    @OptIn(SymbolInternals::class)
    fun constructionVerified(owner: FirRegularClassSymbol, context: CheckerContext): Boolean {
        val declaration = owner.fir
        val path = session.firProvider.getFirClassifierContainerFileIfAny(owner)?.sourceFile?.path
        if (isSkipped(declaration, path)) return false
        return try {
            context(context, DiscardingReporter) {
                val obligations = implementationObligations(declaration) ?: return false
                convert(declaration, obligations, mutableListOf())?.verifiesConstruction == true
            }
        } catch (_: UnsupportedFeatureException) {
            false
        } catch (_: Exception) {
            false
        } catch (_: NotImplementedError) {
            false
        }
    }
}

private object DiscardingReporter : DiagnosticReporter() {
    override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {}
}
