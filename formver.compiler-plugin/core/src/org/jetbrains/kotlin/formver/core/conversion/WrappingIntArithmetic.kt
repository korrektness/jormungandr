package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

fun FirFunctionSymbol<*>.wrapsIntArithmetic(session: FirSession): Boolean =
    hasAnnotation(ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.WrappingIntArithmetic")), session)

/** True for a member `override fun hashCode(): Int`, whose body wraps Int `*` implicitly. */
fun FirFunctionSymbol<*>.overridesHashCode(): Boolean =
    this is FirNamedFunctionSymbol && isOverride && name.asString() == "hashCode" && valueParameterSymbols.isEmpty()
