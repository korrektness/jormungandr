/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/** The specification-only `Multiset` of `formver.annotations`. */
val multisetClassId = ClassId(FqName("org.jetbrains.kotlin.formver.plugin"), Name.identifier("Multiset"))

private val valueTypeClassIds = setOf(
    StandardClassIds.Int,
    StandardClassIds.Boolean,
    StandardClassIds.Char,
    StandardClassIds.String,
    StandardClassIds.Unit,
    multisetClassId,
)

/**
 * Whether [this] is a value type, nullable or not: its values carry no predicate, so ownership means nothing for them.
 */
context(context: CheckerContext)
fun ConeKotlinType.isValueType(): Boolean =
    fullyExpandedType().lowerBoundIfFlexible().classId in valueTypeClassIds
