package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

typealias Path = org.jetbrains.kotlin.formver.common.KotlinPath

context(context: CheckerContext)
fun Path.resolveDeclaredUniqueness(): Uniqueness =
    firstOrNull()?.resolveDeclaredUniqueness()?.join(subList(1, size).resolveDeclaredUniqueness())
        ?: Uniqueness.Unique
