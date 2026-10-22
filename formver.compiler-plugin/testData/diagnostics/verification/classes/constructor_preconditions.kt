// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Sized<!> {
    private var data: @Unique Array<Any?>
    @Law @Pure @Unique fun bounded(): Boolean = data.size <= 1073741824
    constructor(capacity: Int) {
        preconditions { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>0 <= capacity && capacity <= 1073741824<!> }
        data = arrayOfNulls(capacity)
        verify(data.size == capacity)
    }
    constructor() : this(16)
    constructor(valid: Boolean) : this(if (valid) 1 else -1)
}

fun <!VIPER_TEXT!>zeroAndPositive<!>() {
    val zero = Sized(0)
    val positive = Sized(10)
    val delegated = Sized()
}

fun <!VIPER_TEXT!>negativeCapacity<!>() {
    val rejected = <!VIPER_VERIFICATION_ERROR!>Sized(-1)<!>
}

fun <!VIPER_TEXT!>excessCapacity<!>() {
    val rejected = <!VIPER_VERIFICATION_ERROR!>Sized(1073741825)<!>
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class Unrestricted<!> {
    private var data: @Unique Array<Any?>
    @Law @Pure @Unique fun bounded(): Boolean = data.size <= 1073741824
    constructor(capacity: Int) { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>data = arrayOfNulls(capacity)<!> }
}<!>

<!UNSUPPORTED_FEATURE!>class ReadsThis {
    private var data: @Unique Array<Any?> = arrayOfNulls(0)
    constructor(capacity: Int) {
        preconditions { data.size == capacity }
        data = arrayOfNulls(capacity)
    }
}<!>

open <!VIPER_TEXT!>class Parent<!> {
    constructor(capacity: Int) {
        preconditions { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>capacity > 0<!> }
        verify(capacity > 0)
    }
}

<!VIPER_TEXT!>class GoodChild<!> : Parent {
    constructor() : super(1)
}

<!VIPER_TEXT!>class BadChild<!> : Parent {
    constructor() : super(0)
}
