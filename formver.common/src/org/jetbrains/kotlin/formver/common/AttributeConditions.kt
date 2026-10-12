package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType

enum class Locality { Local, Global }
enum class Uniqueness { Unique, Unknown, Shared, Moved }

data class FunctionFact<T>(val parameters: List<Parameter<T>>, val result: FunctionFact<T>?) {
    data class Parameter<T>(val fact: T, val function: FunctionFact<T>?)
}

typealias KotlinPath = List<FirBasedSymbol<*>>

data class ConstructionEscapeFact(
    val escapingClass: FirRegularClassSymbol,
    val kind: Kind,
    val member: FirCallableSymbol<*>?,
    val source: KtSourceElement? = null,
) {
    enum class Kind { MemberCall, Use, Capture }
}

enum class FactPosition { ARGUMENT, RECEIVER, RETURN, THROW, ASSIGNMENT, INITIALIZER }

sealed interface FactTarget {
    data class Value(val position: FactPosition) : FactTarget
    data class Context(val type: ConeKotlinType) : FactTarget
}

sealed interface AttributeCondition : KotlinCondition {
    data class LocalityMismatch(val target: FactTarget, val expected: Locality, val actual: Locality) : AttributeCondition
    data class UniquenessMismatch(val target: FactTarget, val expected: Uniqueness, val actual: Uniqueness) : AttributeCondition
    data class ContractMismatch(
        val target: FactTarget, val expected: FunctionFact<Locality>?, val actual: FunctionFact<Locality>?,
    ) : AttributeCondition
    data class Construction(val position: FactPosition, val escape: ConstructionEscapeFact) : AttributeCondition
    data class LocalCapture(val symbol: FirBasedSymbol<*>, val boundary: FirBasedSymbol<*>) : AttributeCondition
    data class Path(val path: KotlinPath, val other: KotlinPath? = null, val contextType: ConeKotlinType? = null) : AttributeCondition
    data class Rule(val kind: FailureKind) : AttributeCondition
}
