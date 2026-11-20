package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

fun FirFunctionSymbol<*>.writesArraysPointwise(session: FirSession): Boolean =
    hasAnnotation(ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.PointwiseArrayWrites")), session)
