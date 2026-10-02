/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.contracts.FirEffectDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.names.SpecialPackages
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.resolveLocality
import org.jetbrains.kotlin.formver.uniqueness.plugin.Uniqueness
import org.jetbrains.kotlin.formver.uniqueness.plugin.resolveDeclaredUniqueness
import org.jetbrains.kotlin.formver.uniqueness.plugin.resolveResultUniqueness
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.text

// val FirElement.calleeSymbol: FirBasedSymbol<*>
//     get() = toReference()?.toResolvedBaseSymbol()!!
// val FirElement.calleeCallableSymbol: FirCallableSymbol<*>
//     get() = calleeReference?.toResolvedCallableSymbol()!!
val FirFunctionCall.functionCallArguments: List<FirExpression>
    get() = listOfNotNull(dispatchReceiver, extensionReceiver) + argumentList.arguments

val FirFunctionSymbol<*>.effects: List<FirEffectDeclaration>
    get() = this.resolvedContractDescription?.effects ?: emptyList()
/**
 * The kind and source text of this element, for messages. FIR elements have no `toString`, so interpolating one
 * directly prints an identity hash that differs between runs.
 */
val FirElement.description: String
    get() = "${this::class.simpleName} `${source.text}`"

val KtSourceElement?.asPosition: Position
    get() = when (this) {
        null -> Position.NoPosition
        else -> Position.Wrapped(this)
    }
val FirBasedSymbol<*>.asSourceRole: SourceRole
    get() = SourceRole.FirSymbolHolder(this)

fun annotationId(name: String): ClassId =
    ClassId(FqName.fromSegments(SpecialPackages.formver), Name.identifier(name))

private fun callableId(packageName: List<String>, className: String?, name: String): CallableId =
    CallableId(
        FqName.fromSegments(packageName),
        className?.let { FqName.fromSegments(listOf(it)) },
        Name.identifier(name)
    )

fun formverCallableId(className: String?, name: String): CallableId =
    callableId(SpecialPackages.formver, className, name)

fun kotlinCallableId(className: String?, name: String): CallableId = callableId(SpecialPackages.kotlin, className, name)

/**
 * Whether [this] parameter, receiver or property is declared `@Unique`, as the uniqueness checker reads it.
 */
context(context: CheckerContext)
fun FirBasedSymbol<*>.isUnique(): Boolean = resolveDeclaredUniqueness() == Uniqueness.Unique

/**
 * Whether [this] parameter or receiver is borrowed, as the locality checker reads it.
 */
context(context: CheckerContext)
fun FirBasedSymbol<*>.isBorrowed(): Boolean =
    when (this) {
        is FirVariableSymbol<*> -> resolveLocality() == Locality.Local
        is FirReceiverParameterSymbol -> resolveLocality() == Locality.Local
        else -> false
    }

/**
 * Whether calls to [this] function return a unique object, as the uniqueness checker reads it.
 */
fun FirFunctionSymbol<*>.returnsUnique(): Boolean = resolveResultUniqueness() == Uniqueness.Unique

fun FirBasedSymbol<*>.isPure(session: FirSession) = hasAnnotation(annotationId("Pure"), session)

fun FirBasedSymbol<*>.isManual(session: FirSession) = hasAnnotation(annotationId("Manual"), session)

fun FirFunctionSymbol<*>.neverConvert(session: FirSession) = hasAnnotation(annotationId("NeverConvert"), session)

fun FirFunctionSymbol<*>.isFormverFunctionNamed(name: String) =
    this is FirNamedFunctionSymbol && callableId == formverCallableId(className = null, name)

fun FirFunctionSymbol<*>.isInvariantBuilderFunctionNamed(name: String) =
    this is FirNamedFunctionSymbol && callableId == formverCallableId("InvariantBuilder", name)

@OptIn(SymbolInternals::class)
val FirFunctionSymbol<*>.shouldBeInlined
    get() = isInline && fir.body != null

private object ViperProgram : FirDeclarationDataKey()
private object ShouldVerify : FirDeclarationDataKey()

var FirSimpleFunction.viperProgram: viper.silver.ast.Program? by FirDeclarationDataRegistry.data(ViperProgram)
var FirSimpleFunction.shouldVerify: Boolean? by FirDeclarationDataRegistry.data(ShouldVerify)
