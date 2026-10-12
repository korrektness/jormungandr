package org.jetbrains.kotlin.formver.locality.contract.plugin

import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.type.plugin.TypeFactDiagnostic

private fun LocalityContract.toFact(): FunctionFact<Locality> = FunctionFact(
    parameterTypeFacts.map { FunctionFact.Parameter(it.typeFact, it.functionTypeFact?.toFact()) },
    resultFunctionTypeFact?.toFact(),
)

object LocalityContractErrors {
    private fun diagnostic(kind: FailureKind) = TypeFactDiagnostic<LocalityContract?>(kind) { target, expected, actual ->
        AttributeCondition.ContractMismatch(target, expected?.toFact(), actual?.toFact())
    }
    val LOCALITY_CONTRACT_MISMATCH = diagnostic(FailureKind.LOCALITY_CONTRACT_MISMATCH)
    val CONTEXT_LOCALITY_CONTRACT_MISMATCH = diagnostic(FailureKind.CONTEXT_LOCALITY_CONTRACT_MISMATCH)
}
