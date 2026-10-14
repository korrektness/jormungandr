/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Function
import org.jetbrains.kotlin.formver.viper.ast.Type

/**
 * The value of an exhaustive `when` of runtime type [runtimeType] on the path where no branch matches. Its argument is
 * what the subject's type guarantees, and its precondition requires the path to contradict it, so every call proves
 * the path unreachable.
 *
 * The postcondition, given [runtimeType], types the result as the `when`, so that code using the value type-checks; a
 * path that reaches the call is unreachable, so it states nothing about a real value. Each type gets its own function:
 * a postcondition quantified over the type would claim a value of an uninhabited type too, and an uninhabited type
 * gets no postcondition.
 */
fun unmatchedWhenFunction(name: SymbolicName, runtimeType: Exp?): Function = FunctionBuilder.build(name) {
    val guaranteed = argument(Type.Bool)
    returns(Type.Ref)
    precondition { Exp.Not(guaranteed) }
    runtimeType?.let { postcondition { result isOf it } }
}
