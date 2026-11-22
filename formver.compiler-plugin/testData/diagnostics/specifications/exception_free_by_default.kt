// FULL_JDK
// EXCEPTION_FREE_BY_DEFAULT

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>plain<!>(x: Int): Int = x

fun <!VIPER_TEXT!>callsExceptionFree<!>(x: Int): Int = plain(x)

fun <!VIPER_TEXT!>throws<!>(x: Int): Int {
    if (x < 0) <!EXCEPTION_FROM_THROW!>throw <!EXCEPTION_FROM_THROW!>IllegalArgumentException()<!><!>
    return x
}

fun <!VIPER_TEXT!>requires<!>(x: Int): Int {
    <!EXCEPTION_FROM_THROW!>require(x >= 0)<!>
    return x
}

fun <!VIPER_TEXT!>asserts<!>(x: Int?): Int = <!EXCEPTION_FROM_THROW!>x!!<!>

fun <!VIPER_TEXT!>throwing<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    if (x < 0) throw IllegalArgumentException()
    return x
}

fun <!VIPER_TEXT!>callsThrowingSafely<!>(x: Int): Int {
    if (x < 0) return 0
    return throwing(x)
}

fun <!VIPER_TEXT!>callsThrowing<!>(x: Int): Int = <!EXCEPTION_FROM_THROWING_CALL!>throwing(x)<!>

fun <!VIPER_TEXT!>ownConditionKept<!>(x: Int): Int {
    throwsOnlyIf { x == 0 }
    if (x < 0) <!THROWS_CONDITION_NOT_ESTABLISHED!>throw <!THROWS_CONDITION_NOT_ESTABLISHED!>IllegalArgumentException()<!><!>
    return x
}

fun <!VIPER_TEXT!>invokes<!>(f: (Int) -> Int, x: Int): Int = <!EXCEPTION_FROM_FUNCTION_OBJECT!>f(x)<!>

fun <!VIPER_TEXT!>caught<!>(x: Int): Int {
    try {
        if (x < 0) <!EXCEPTION_IN_TRY!>throw <!EXCEPTION_IN_TRY!>IllegalArgumentException()<!><!>
    } catch (e: IllegalArgumentException) {
        return 0
    }
    return x
}

fun <!VIPER_TEXT!>finallyOnce<!>(x: Int): Int {
    try {
        if (x < 0) <!EXCEPTION_FROM_THROW!>throw <!EXCEPTION_FROM_THROW!>IllegalArgumentException()<!><!>
    } finally {
        verify(true)
    }
    return x
}

@Pure
fun <!VIPER_TEXT!>pureIdentity<!>(x: Int): Int = x

fun <!VIPER_TEXT!>callsPure<!>(x: Int): Int = pureIdentity(x)

<!VIPER_TEXT!>class Point<!>(val x: Int, val y: Int)

fun <!VIPER_TEXT!>constructs<!>(x: Int): Point = Point(x, x)

<!VIPER_TEXT!>class Positive<!>(val value: Int) {
    init {
        <!EXCEPTION_FROM_THROW!>require(value > 0)<!>
    }
}

fun <!VIPER_TEXT!>constructsChecked<!>(x: Int): Positive = Positive(x)

<!VIPER_TEXT!>class Named<!>(val name: String) {
    override fun <!VIPER_TEXT!>toString<!>(): String = name
}

<!VIPER_TEXT!>class Broken<!> {
    override fun <!VIPER_TEXT!>toString<!>(): String = <!EXCEPTION_FROM_THROW!>throw <!EXCEPTION_FROM_THROW!>UnsupportedOperationException()<!><!>
}

fun <!VIPER_TEXT!>showsNamed<!>(n: Named): String = n.toString()

fun <!VIPER_TEXT!>showsAny<!>(a: Any): String = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>a.toString()<!>

open <!VIPER_TEXT!>class Base<!> {
    open fun <!VIPER_TEXT!>get<!>(x: Int): Int = x
}

<!VIPER_TEXT!>class Refined<!> : Base() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>get<!>(x: Int): Int {
        throwsOnlyIf { x < 0 }
        if (x < 0) throw IllegalArgumentException()
        return x
    }
}

fun <!VIPER_TEXT!>callsThroughBase<!>(b: Base, x: Int): Int = b.get(x)

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>checkedInline<!>(x: Int): Int {
    <!EXCEPTION_FROM_THROW!>require(x >= 0)<!>
    return x
}

fun <!VIPER_TEXT!>inlinesChecked<!>(x: Int): Int = checkedInline(x)

<!VIPER_TEXT!>class Gauge<!>(private val raw: Int) {
    val level: Int
        <!VIPER_TEXT!>get<!>() = raw
}

fun <!VIPER_TEXT!>readsAccessor<!>(g: Gauge): Int = <!EXCEPTION_FROM_UNSPECIFIED_CALL!>g.level<!>
