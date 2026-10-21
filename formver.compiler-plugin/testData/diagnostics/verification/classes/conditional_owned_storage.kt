// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class ConditionalStorage<!> {
    private var data: @Unique Array<Any?>

    constructor(capacity: Int) {
        data = when {
            capacity == 0 -> arrayOfNulls(0)
            capacity > 0 -> arrayOfNulls(capacity)
            else -> throw IllegalArgumentException("capacity: $capacity")
        }
        verify((capacity == 0) implies (data.size == 0))
        verify((capacity > 0) implies (data.size == capacity))
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>reset<!>(capacity: Int, value: Any?) {
        preconditions { capacity > 0 }
        data = when {
            capacity > 0 -> arrayOfNulls(capacity)
            else -> throw IllegalArgumentException()
        }
        data[0] = value
        verify(data[0] === value)
    }
}

<!VIPER_TEXT!>class LocalThenAssign<!> {
    private var data: @Unique Array<Any?>
    constructor(capacity: Int) {
        val d: @Unique Array<Any?> = when {
            capacity == 0 -> arrayOfNulls(0)
            capacity > 0 -> arrayOfNulls(capacity)
            else -> throw IllegalArgumentException()
        }
        data = d
        verify(data.size == capacity)
    }
}

fun <!VIPER_TEXT!>failCapacity<!>(): @Unique Nothing = throw IllegalArgumentException()

<!UNSUPPORTED_FEATURE!>class NothingArm {
    private var data: @Unique Array<Any?>
    constructor(capacity: Int) {
        data = when {
            capacity >= 0 -> arrayOfNulls(capacity)
            else -> failCapacity()
        }
        verify(data.size == capacity)
    }
}<!>

fun <!VIPER_TEXT!>failUnannotated<!>(): Nothing = throw IllegalArgumentException()

<!VIPER_TEXT!>class UnannotatedNothingArm<!> {
    private var data: @Unique Array<Any?>
    <!VERIFICATION_SKIPPED!>constructor(capacity: Int) {
        data = <!UNIQUENESS_MISMATCH!>when {
            capacity >= 0 -> arrayOfNulls(capacity)
            else -> failUnannotated()
        }<!>
    }<!>
}

<!VIPER_TEXT!>class WrongSize<!> {
    private var data: @Unique Array<Any?>
    constructor(capacity: Int) {
        data = when {
            capacity == 0 -> arrayOfNulls(0)
            capacity > 0 -> arrayOfNulls(capacity)
            else -> throw IllegalArgumentException()
        }
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>data.size > 0<!>)
    }
}

fun <!VIPER_TEXT!>sharedArray<!>(): Array<Any?> = arrayOfNulls(0)

<!VIPER_TEXT!>class SharedArm<!> {
    private var data: @Unique Array<Any?>
    <!VERIFICATION_SKIPPED!>constructor(capacity: Int) {
        data = <!UNIQUENESS_MISMATCH!>when {
            capacity > 0 -> arrayOfNulls(capacity)
            else -> sharedArray()
        }<!>
    }<!>
}

<!VIPER_TEXT!>class LiveState<!> {
    private var value: Int = 0

    @Unique @Borrowed fun <!VIPER_TEXT!>checkedConcat<!>(a: String, b: String) {
        preconditions { a.length <= 100; b.length <= 100 }
        val joined = a + b
        value = 7
        verify(value == 7)
        verify(joined.length == a.length + b.length)
    }
}
