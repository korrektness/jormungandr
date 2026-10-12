/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.embeddings.callables.SpecialFunctions
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.core.linearization.subExps
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp

internal fun Exp.isStableCaptureFact(context: ProgramConversionContext): Boolean = StableCaptureFacts(context).stable(this)

private class StableCaptureFacts(private val context: ProgramConversionContext) {
    private val visitedFunctions = mutableSetOf<SymbolicName>()

    fun stable(exp: Exp): Boolean {
        if (exp.hasOwnershipAssertion()) return false
        return when (exp) {
            is Exp.FieldAccess -> context.typeResolver.isImmutableField(exp.field.name) && stable(exp.rcv)
            is Exp.Unfolding -> stable(exp.body)
            is Exp.FuncApp -> exp.args.all(::stable) && stableFunction(exp.functionName)
            else -> exp.subExps().all(::stable)
        }
    }

    private fun stableFunction(name: SymbolicName): Boolean {
        if (!visitedFunctions.add(name)) return true
        val signature = context.lookupFunctionSignature(name)
        if (signature != null && signature.preconditions.pureToViper(toBuiltin = true, context.typeResolver)
                .any { it.hasOwnershipAssertion() }) return false
        val body = context.convertedBodyResolver.lookupPure(name)
        if (body != null) return body.subtree().all { node ->
            when (node) {
                is FieldAccess -> context.typeResolver.isImmutableField(node.field.name)
                is PrimitiveFieldAccess -> context.typeResolver.isImmutableField(node.field.name)
                is FunctionCall -> stableFunction(node.function.name)
                is ExpWrapper -> stable(node.value)
                else -> true
            }
        }
        val special = (SpecialFunctions.all + SpecialFunctions.multiset + IntArrayEmbedding.arraySizeFunction)
            .firstOrNull { it.name == name }
        if (special != null) return special.body?.let(::stable) ?: special.pres.none { it.hasOwnershipAssertion() }
        return signature?.formalArgs?.none { it.isUnique || it.isBorrowed } == true
    }
}
