/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.callables.FunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpWrapper
import org.jetbrains.kotlin.formver.core.embeddings.expression.PlaceholderVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.asTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.embeddings.types.injection
import org.jetbrains.kotlin.formver.core.names.InheritedMemberName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.ast.Exp

/** A proof-only signature with ownership and observations in the receiving runtime class. */
internal fun ProgramConverter.specializeInheritedMember(
    owner: FirRegularClassSymbol,
    symbol: FirNamedFunctionSymbol,
    role: String,
    contract: FirNamedFunctionSymbol = symbol,
): SignatureWithTarget<NonInlineFunctionSignature> {
    val view = embedClass(owner).asTypeEmbedding()
    val original = with(this) { symbol.toFunctionSignature() }
    val receiver = original.signature.dispatchReceiver ?: error("Inherited member has no receiver")
    val specialized = FunctionSignatureImpl(
        original.signature.callableType.copy(dispatchReceiverType = view),
        PlaceholderVariableEmbedding(receiver.name, view, receiver.isUnique, receiver.isBorrowed),
        original.signature.extensionReceiver,
        original.signature.params,
        original.signature.returns,
        original.signature.isPure,
        original.signature.labelName,
    )
    val name = ScopedName(owner.classId.embedName().asScope(),
        InheritedMemberName(symbol.embedName(this), role, contract.embedName(this)))
    val named = NamedFunctionSignatureImpl(specialized, name, symbol)
    val complete = with(this) {
        SignatureWithTarget(named, original.returnTarget).toNonInlineSignature(symbol).toCompleteSignature(owner.source) {
            userFunctionContract()
            val (preconditions, postconditions) = embedContract(contract, named, original.returnTarget)
            addPreconditions(preconditions)
            addPostconditions(postconditions)
        }
    }
    val boolean = buildType { boolean() }
    val exactRuntimeClass = ExpWrapper.deferred(boolean) {
        boolean.injection.toRef(Exp.EqCmp(
            RuntimeTypeDomain.typeOf(specialized.dispatchReceiver!!.toLocalVarUse()), view.runtimeType,
        ))
    }
    return complete.copy(signature = complete.signature.copy(
        preconditions = complete.signature.preconditions + exactRuntimeClass,
        declarationSource = owner.source,
    ))
}
