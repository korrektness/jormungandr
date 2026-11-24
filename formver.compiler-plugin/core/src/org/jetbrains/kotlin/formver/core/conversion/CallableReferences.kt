/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.isBound
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isBasicFunctionOrKFunctionType
import org.jetbrains.kotlin.fir.types.isNullableNothing
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.common.isLibraryDeclaration
import org.jetbrains.kotlin.formver.core.contextAndExplicitArguments
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.CallableReferenceValue
import org.jetbrains.kotlin.formver.core.embeddings.expression.Cast
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.UnitLit
import org.jetbrains.kotlin.formver.core.embeddings.expression.blockOf
import org.jetbrains.kotlin.formver.core.embeddings.expression.toBlock
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

private val materializedUnitResult = Cast(UnitLit, UnitLit.type)

internal fun StmtConversionContext.convertCallableReference(reference: FirCallableReferenceAccess): CallableReferenceValue {
    val symbol = reference.toResolvedCallableSymbol()
    val target = symbol as? FirFunctionSymbol<*>
        ?: reference.unsupported("to ${symbol?.callableId ?: reference.calleeReference.name} resolves to no function")
    val id = target.callableId
    fun unsupported(reason: String): Nothing = reference.unsupported("to $id $reason")
    val functionType = reference.resolvedType
    unsupportedReferenceSignature(target, functionType)?.let { unsupported(it) }
    val receiverTypes = listOfNotNull(target.dispatchReceiverType, target.resolvedReceiverType)
    if (receiverTypes.size > 1) unsupported("has ${receiverTypes.size} receivers")
    val bound = reference.isBound
    val receiver = if (bound) checkNotNull(reference.dispatchReceiver ?: reference.extensionReceiver) {
        "Bound callable reference to ${target.callableId} has no receiver"
    } else null
    if (bound && receiverTypes.isEmpty()) unsupported("has a bound receiver but no receiver formal")
    val parameters = functionType.typeArguments.dropLast(1).map { it.type ?: unsupported("has a projected argument $it") }
    val targetArity = parameters.size + if (bound) 1 else 0
    val targetFormals = target.valueParameterSymbols.size + receiverTypes.size
    if (targetArity != targetFormals) unsupported("has adapter arity $targetArity and $targetFormals target formals")
    val result = functionType.typeArguments.lastOrNull()?.type ?: unsupported("has no result type in $functionType")
    val receiverType = receiver?.resolvedType ?: parameters.singleOrNull()
    val stringTrim = target.isStringTrim(receiverType, result)
    val fileFromPath = target.isFileFromPath()
    if (stringTrim || fileFromPath) requireUnmodeledReferenceCall(target, reference)
    val capture = receiver?.let {
        val ownership = referenceOwnershipRestriction(reference, listOf(it))
        if (ownership != null) unsupported("captures a receiver while $ownership")
        val value = convertExpecting(it, receiverTypes.single())
        declareAnonVar(value.type, value)
    }
    val callee = if (stringTrim) null else referenceTarget(reference, target, targetArity, fileFromPath)
    return CallableReferenceValue(embedType(reference), reference.source, id.toString(), capture) { args, expected ->
        if (args.size != parameters.size) unsupported("is invoked with ${args.size} arguments; expected ${parameters.size}")
        val targetTypes = receiverTypes + target.valueParameterSymbols.map { it.resolvedReturnType }
        val operands = (listOfNotNull(capture?.variable) + args).zip(targetTypes).map { (value, formal) ->
            CallOperand(null, value, formal.takeIf { value.type.isNullable && !embedType(it).isNullable })
        }
        val call = evaluateOperands(operands).around { actual ->
            if (stringTrim) return@around blockOf(actual.single(), unconstrainedValue(embedType(result)))
            val signature = checkNotNull(callee) { "Callable reference to ${target.callableId} has no embedded signature" }
            if (fileFromPath) invokeFileReference(signature, actual.single(), embedType(target.resolvedReturnType))
            else signature.insertCall(actual, this, if (result.isUnit) signature.callableType.returnType else embedType(result))
        }
        if (result.isUnit || expected == UnitLit.type) blockOf(call, materializedUnitResult) else call
    }
}

private fun StmtConversionContext.unsupportedReferenceSignature(target: FirFunctionSymbol<*>, type: ConeKotlinType): String? = when {
    target.resolvedStatus.isSuspend -> "targets a suspend function"
    !type.isBasicFunctionOrKFunctionType(session) -> "has unsupported function type $type"
    target.callableId?.isLocal == true -> "targets a local function"
    target.contextParameterSymbols.isNotEmpty() -> "targets a function with context parameters"
    target.valueParameterSymbols.any { it.isVararg } -> "targets a function with a vararg formal"
    target.resolvedReturnType is ConeFlexibleType -> "has platform result type ${target.resolvedReturnType}"
    else -> null
}

private fun StmtConversionContext.invokeFileReference(
    callee: NonInlineFunctionSignature,
    path: ExpEmbedding,
    resultType: TypeEmbedding,
): ExpEmbedding {
    val checked = if (path.type.isNullable) checkedCast(path, path.type.getNonNullable()) else path
    return withoutExceptionalExits { callee.insertCall(listOf(checked), this, resultType) }
}

private fun FirCallableReferenceAccess.unsupported(reason: String): Nothing =
    throw UnsupportedFeatureException(source, "callable reference $reason")

/** On a String (not an arbitrary CharSequence), this overload invokes no user code. */
private fun FirFunctionSymbol<*>.isStringTrim(receiver: ConeKotlinType?, result: ConeKotlinType): Boolean {
    val id = callableId ?: return false
    if (!isLibraryDeclaration() || id.packageName != FqName("kotlin.text") || id.callableName.asString() != "trim") return false
    return valueParameterSymbols.isEmpty() && receiver?.classId == StandardClassIds.String && result.classId == StandardClassIds.String
}

private fun FirFunctionSymbol<*>.isFileFromPath(): Boolean {
    if (this !is FirConstructorSymbol || !isLibraryDeclaration()) return false
    return callableId?.classId == ClassId.topLevel(FqName("java.io.File")) &&
        valueParameterSymbols.singleOrNull()?.resolvedReturnType?.classId == StandardClassIds.String
}

private fun StmtConversionContext.referenceTarget(
    reference: FirCallableReferenceAccess,
    target: FirFunctionSymbol<*>,
    arity: Int,
    fileFromPath: Boolean,
): NonInlineFunctionSignature {
    val id = target.callableId
    if (target.isLibraryDeclaration() && !fileFromPath) reference.unsupported("to $id has no library reference adapter")
    val callee = embedAnyFunction(target) as? NonInlineFunctionSignature
        ?: reference.unsupported("to $id has no ordinary call model")
    if (callee.formalArgs.size != arity) {
        reference.unsupported("to $id has ${callee.formalArgs.size} embedded formals and adapter arity $arity")
    }
    return callee
}

internal fun StmtConversionContext.convertExplicitReferenceInvocation(call: FirFunctionCall): ExpEmbedding? {
    if (call.calleeReference.name.asString() != "invoke") return null
    val symbol = call.toResolvedCallableSymbol() ?: return null
    if (symbol.callableId?.packageName != FqName("kotlin")) return null
    return call.dispatchReceiver?.let { convertReferenceInvocation(call, it) }
}

/** Finds a statically known callback without lowering a literal or its receiver until the result is called. */
internal fun StmtConversionContext.knownCallableReference(expression: FirExpression): (() -> CallableReferenceValue)? = when (expression) {
    is FirCallableReferenceAccess -> { { convertCallableReference(expression) } }
    is FirPropertyAccessExpression -> (expression.calleeReference.symbol as? FirPropertySymbol)
        ?.takeIf { it.isLocal && capturedAccess(it) == null }?.let(::resolveCallableReference)?.let { value -> { value } }
    else -> null
}

internal fun StmtConversionContext.invokeCallableReference(
    call: FirElement,
    adapter: CallableReferenceValue,
    arguments: List<ExpEmbedding>,
    result: TypeEmbedding,
): ExpEmbedding = withCallSite(call) { adapter.call(arguments, result, this) }

/** A built-in reference adapter is available only for a call with no bound external specification. */
internal fun StmtConversionContext.requireUnmodeledReferenceCall(callee: FirCallableSymbol<*>, call: FirElement) {
    if (callee.externalSpecificationSymbol(session) != callee) {
        throw UnsupportedFeatureException(call.source, "callable reference adapter for ${callee.callableId} with a bound external specification")
    }
}

/** Callable adapters do not model ownership effects of the target in the caller's flow analysis. */
internal fun StmtConversionContext.requireSharedReferenceInvocation(call: FirFunctionCall, arguments: List<FirExpression>) {
    val restriction = referenceOwnershipRestriction(call, arguments) ?: return
    val callee = call.toResolvedCallableSymbol()?.callableId ?: call.calleeReference.name
    throw UnsupportedFeatureException(call.source, "callable reference invocation through $callee while $restriction")
}

private fun StmtConversionContext.referenceOwnershipRestriction(site: FirElement, operands: List<FirExpression>): String? {
    if (holdsOwnership()) return "the enclosing body holds ownership"
    val index = operands.indexOfFirst { !it.unwrapArgument().resolvedType.isNullableNothing && ownsReceiverBefore(site, it) }
    if (index < 0) return null
    return "operand ${index + 1} of type ${operands[index].resolvedType} carries ownership"
}

internal fun StmtConversionContext.convertReferenceInvocation(call: FirFunctionCall, receiver: FirExpression): ExpEmbedding? {
    val reference = knownCallableReference(receiver) ?: return null
    val callee = checkNotNull(call.toResolvedCallableSymbol()) { "Callable reference invocation has no resolved ${call.calleeReference.name} callee" }
    requireUnmodeledReferenceCall(callee, call)
    val arguments = call.contextAndExplicitArguments
    requireSharedReferenceInvocation(call, arguments)
    val adapter = reference()
    val operands = arguments.map { operandOf(call, it) }
    val snapshots = operands.takeIf { it.size > 1 }?.map { declareAnonVar(it.value.type, it.value) }.orEmpty()
    val inputs = if (snapshots.isEmpty()) operands else operands.zip(snapshots) { operand, saved ->
        operand.copy(value = saved.variable)
    }
    return (listOfNotNull(adapter.capture) + snapshots + evaluateOperands(inputs).around { args ->
        invokeCallableReference(call, adapter.captured(), args, embedType(call))
    }).toBlock()
}
