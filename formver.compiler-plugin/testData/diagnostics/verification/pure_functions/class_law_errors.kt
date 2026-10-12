// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

interface Observation {
    @Pure fun size(): Int
    @Pure val amount: Int
}

abstract class AbstractObservation : Observation {
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>size()<!> >= 0
}

open class InheritedObservation : Observation {
    @Pure override fun size(): Int = 1
    @Pure override val amount: Int = 1
}

class InheritedInvariant : InheritedObservation() {
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>size()<!> >= 0
    @Law @Pure fun propertyInvariant(): Boolean = <!INVALID_LAW!>amount<!> >= 0
}

open class OpenObservation : Observation {
    @Pure override fun size(): Int = 1
    @Pure override val amount: Int get() = 1
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>size()<!> >= 0
    @Law @Pure fun propertyInvariant(): Boolean = <!INVALID_LAW!>amount<!> >= 0
}

class RecursiveObservation : Observation {
    @Pure override fun size(): Int = again()
    @Pure fun again(): Int = size()
    @Pure override val amount: Int get() = amount
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>size()<!> >= 0
    @Law @Pure fun propertyInvariant(): Boolean = <!INVALID_LAW!>amount<!> >= 0
}

open class FinalObservation : Observation {
    @Pure final override fun size(): Int = 1
    @Pure final override val amount: Int get() = 1
    @Law @Pure fun invariant(): Boolean = size() >= 0 && amount >= 0
}

class InvalidExpandedBody {
    var state = 0
    @Pure fun observation(): Int {
        state = 1
        return state
    }
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>observation()<!> >= 0
}

<!INVALID_LAW!>class SecondaryInvariant(val value: Int) {
    constructor() : this(1)
    @Law @Pure fun invariant(): Boolean = value >= 0
}<!>

<!INVALID_LAW!>class InitInvariant(val value: Int) {
    init { require(value >= 0) }
    @Law @Pure fun invariant(): Boolean = value >= 0
}<!>

var externalState = 0
fun impureInitializer(value: Int): Int = value

<!INVALID_LAW!>class ImpureInitializerInvariant(value: Int) {
    val initialized = impureInitializer(value)
    @Law @Pure fun invariant(): Boolean = initialized >= 0
}<!>

<!INVALID_LAW!>class ExternalInitializerInvariant {
    val initialized = externalState
    @Law @Pure fun invariant(): Boolean = initialized >= 0
}<!>

open class SuperInvariant(val value: Int) {
    @Law @Pure fun invariant(): Boolean = value >= 0
}

<!INVALID_LAW!>class SubInvariant(value: Int) : SuperInvariant(value)<!>

<!INVALID_LAW!>class PropertyInitializerInvariant(value: Int) {
    val first = value
    val second = first
    @Law @Pure fun invariant(): Boolean = second >= 0
}<!>

<!INVALID_LAW!>class LambdaInitializerInvariant(value: Int) {
    val function = { parameter: Int -> parameter + value }
    @Law @Pure fun invariant(): Boolean = true
}<!>

<!INVALID_LAW!>class LocalFunctionInitializerInvariant(value: Int) {
    val initialized = if (value > 0) {
        @Pure fun local(parameter: Int): Int = parameter
        local(value)
    } else value
    @Law @Pure fun invariant(): Boolean = initialized >= 0
}<!>
