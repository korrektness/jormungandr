package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.VirtualFileKotlinClass
import org.jetbrains.kotlin.metadata.deserialization.TypeTable
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmMemberSignature
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.VarInsnNode

/** Directly supplied lambdas of the JVM contract markers that do not invoke their arguments. */
data class ContractLambda(val owner: FirFunction, val declaration: FirAnonymousFunction, val reason: Reason) {
    enum class Reason { PRECONDITION, POSTCONDITION }

    val body: FirBlock? get() = declaration.body
}

/** Candidates for spec-only evidence; each still needs an emitted contract and callable body. */
fun FirFunction.contractLambdaCandidates(): List<ContractLambda> {
    val statements = when (this) {
        is FirSimpleFunction -> body?.statements.orEmpty()
        is FirAnonymousFunction -> body?.liftedContractStatements().orEmpty()
        else -> return emptyList()
    }
    val pre = statements.firstOrNull() as? FirFunctionCall
    val hasPre = pre?.markerSymbol("preconditions") != null
    val post = statements.getOrNull(if (hasPre) 1 else 0) as? FirFunctionCall
    return listOfNotNull(
        pre?.contractLambda(this, "preconditions", ContractLambda.Reason.PRECONDITION, 0),
        post?.contractLambda(this, "postconditions", ContractLambda.Reason.POSTCONDITION, 1),
    )
}

private fun FirFunctionCall.markerSymbol(name: String) =
    (toResolvedCallableSymbol() as? FirNamedFunctionSymbol)?.takeIf { it.isFormverFunctionNamed(name) }

@OptIn(SymbolInternals::class)
private fun FirFunctionCall.contractLambda(
    owner: FirFunction, name: String, reason: ContractLambda.Reason, arity: Int,
): ContractLambda? {
    val symbol = markerSymbol(name) ?: return null
    val lambda = (arguments.singleOrNull() as? FirAnonymousFunctionExpression)?.anonymousFunction ?: return null
    if (!lambda.isLambda) return null
    val declaration = symbol.fir
    val container = declaration.containerSource as? JvmPackagePartSource ?: return null
    if (symbol.isInline || symbol.resolvedReturnType.classId != StandardClassIds.Unit) return null
    if (declaration.origin != FirDeclarationOrigin.Library ||
        container.className.internalName != "org/jetbrains/kotlin/formver/plugin/BuiltinsKt"
    ) return null
    if (symbol.valueParameterSymbols.singleOrNull()?.resolvedReturnType?.classId != StandardClassIds.FunctionN(arity)) return null
    val noOp = declaration.markerDoesNotInvoke ?: container.markerDoesNotInvoke(name, arity).also {
        declaration.markerDoesNotInvoke = it
    }
    return if (noOp) ContractLambda(owner, lambda, reason) else null
}

private object MarkerDoesNotInvoke : FirDeclarationDataKey()
private var FirSimpleFunction.markerDoesNotInvoke: Boolean? by FirDeclarationDataRegistry.data(MarkerDoesNotInvoke)

private fun JvmPackagePartSource.markerDoesNotInvoke(name: String, arity: Int): Boolean {
    if (facadeClassName != null) return false
    val binary = knownJvmBinaryClass as? VirtualFileKotlinClass ?: return false
    val signature = binary.markerSignature(name) ?: return false
    if (signature.desc != "(Lkotlin/jvm/functions/Function$arity;)V") return false
    val jarClass = ClassNode()
    ClassReader(binary.file.contentsToByteArray()).accept(jarClass, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    if (jarClass.name != className.internalName) return false
    val method = jarClass.methods.singleOrNull { it.name == signature.name && it.desc == signature.desc } ?: return false
    return method.doesNotInvokeArgument()
}

private fun VirtualFileKotlinClass.markerSignature(name: String): JvmMemberSignature.Method? {
    val data = classHeader.data ?: return null
    val strings = classHeader.strings ?: return null
    val (names, pkg) = JvmProtoBufUtil.readPackageDataFrom(data, strings)
    val function = pkg.functionList.singleOrNull { names.getString(it.name) == name } ?: return null
    return JvmProtoBufUtil.getJvmMethodSignature(function, names, TypeTable(pkg.typeTable))
}

private fun MethodNode.doesNotInvokeArgument(): Boolean {
    val unsupported = Opcodes.ACC_NATIVE or Opcodes.ACC_ABSTRACT
    if (access and Opcodes.ACC_STATIC == 0 || access and unsupported != 0 || tryCatchBlocks.isNotEmpty()) return false
    val instructions = instructions.toArray().filter { it.opcode >= 0 }
    if (instructions.map { it.opcode } == listOf(Opcodes.RETURN)) return true
    if (instructions.map { it.opcode } != listOf(Opcodes.ALOAD, Opcodes.LDC, Opcodes.INVOKESTATIC, Opcodes.RETURN)) return false
    val load = instructions[0] as VarInsnNode
    val parameterName = instructions[1] as LdcInsnNode
    val check = instructions[2] as MethodInsnNode
    return load.`var` == 0 && parameterName.cst is String &&
        check.owner == "kotlin/jvm/internal/Intrinsics" && check.name == "checkNotNullParameter" &&
        check.desc == "(Ljava/lang/Object;Ljava/lang/String;)V" && !check.itf
}

/** Callable provenance of a single emitted verification program, independently of its outcome. */
data class EmittedCallableEvidence(
    val bodies: List<FirFunction>,
    val contractLambdas: Set<ContractLambda>,
)

internal class EmbeddedContractLambdas {
    private val lambdas = mutableMapOf<SymbolicName, List<ContractLambda>>()

    fun record(signature: SymbolicName, holder: FirFunction, pre: FirBlock?, post: FirBlock?) {
        lambdas[signature] = holder.contractLambdaCandidates().filter { lambda ->
            when (lambda.reason) {
                ContractLambda.Reason.PRECONDITION -> pre != null && lambda.body === pre
                ContractLambda.Reason.POSTCONDITION -> post != null && lambda.body === post
            }
        }
    }

    fun emitted(owner: FirFunction, copies: List<RegisteredFunction>): Set<ContractLambda> = copies.map { obligation ->
        lambdas[obligation.signature.name].orEmpty().filter { it.owner === owner }.toSet()
    }.reduce(Set<ContractLambda>::intersect)
}
