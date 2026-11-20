// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Box<!>(var value: Int)

fun <!VIPER_TEXT!>throwChecked<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    if (x < 0) throw IllegalArgumentException()
    return x
}

fun <!VIPER_TEXT!>throwTooOften<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    if (x <= 0) <!THROWS_CONDITION_NOT_ESTABLISHED!>throw <!THROWS_CONDITION_NOT_ESTABLISHED!>IllegalArgumentException()<!><!>
    return x
}

fun <!VIPER_TEXT!>requireChecked<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    require(x >= 0)
    return x
}

fun <!VIPER_TEXT!>requireTooStrict<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    <!THROWS_CONDITION_NOT_ESTABLISHED!>require(x > 0)<!>
    return x
}

fun <!VIPER_TEXT!>checkChecked<!>(x: Int): Int {
    throwsOnlyIf { x == 0 }
    check(x != 0) { "zero" }
    return x
}

fun <!VIPER_TEXT!>checkTooStrict<!>(x: Int): Int {
    throwsOnlyIf { x == 0 }
    <!THROWS_CONDITION_NOT_ESTABLISHED!>check(x > 0) { "not positive" }<!>
    return x
}

fun <!VIPER_TEXT!>errorChecked<!>(x: Int): Int {
    throwsOnlyIf { x == 1 }
    if (x == 1) error("one")
    return x
}

fun <!VIPER_TEXT!>errorUnconditional<!>(x: Int): Int {
    throwsOnlyIf { x == 1 }
    <!THROWS_CONDITION_NOT_ESTABLISHED!>error("always")<!>
}

fun <!VIPER_TEXT!>nullAssertionChecked<!>(x: Int?): Int {
    throwsOnlyIf { x == null }
    return x!!
}

fun <!VIPER_TEXT!>nullAssertionForbidden<!>(x: Int?): Int {
    throwsOnlyIf { false }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>x!!<!>
}

fun <!VIPER_TEXT!>castChecked<!>(x: Any): String {
    throwsOnlyIf { x !is String }
    return x as String
}

fun <!VIPER_TEXT!>castForbidden<!>(x: Any): String {
    throwsOnlyIf { x is Int }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>x as String<!>
}

fun <!VIPER_TEXT!>callerAssumesCallee<!>(x: Int): Int {
    throwsOnlyIf { x < 0 }
    return throwChecked(x)
}

fun <!VIPER_TEXT!>callerTooStrict<!>(x: Int): Int {
    throwsOnlyIf { x < -1 }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>throwChecked(x)<!>
}

fun <!VIPER_TEXT!>plain<!>(x: Int): Int = x

fun <!VIPER_TEXT!>needsPositive<!>(x: Int): Int {
    preconditions { x > 0 }
    throwsOnlyIf { x == 1 }
    if (x == 1) throw IllegalArgumentException()
    return x
}

fun <!VIPER_TEXT!>callerBreaksPrecondition<!>(x: Int): Int {
    throwsOnlyIf { true }
    return <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(x)<!>
}

fun <!VIPER_TEXT!>callerOfUnspecified<!>(x: Int): Int {
    throwsOnlyIf { x == 0 }
    if (x != 0) return x
    return plain(x)
}

fun <!VIPER_TEXT!>callerOfUnspecifiedForbidden<!>(x: Int): Int {
    throwsOnlyIf { x == 0 }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>plain(x)<!>
}

fun <!VIPER_TEXT!>entryState<!>(b: @Unique @Borrowed Box): Int {
    throwsOnlyIf { old(b.value) == 0 }
    val v = b.value
    b.value = 0
    if (v == 0) throw IllegalStateException()
    return v
}

fun <!VIPER_TEXT!>currentStateIsNotEntryState<!>(b: @Unique @Borrowed Box): Int {
    throwsOnlyIf { b.value == 0 }
    b.value = 0
    if (b.value == 0) <!THROWS_CONDITION_NOT_ESTABLISHED!>throw <!THROWS_CONDITION_NOT_ESTABLISHED!>IllegalStateException()<!><!>
    return 1
}

fun <!VIPER_TEXT!>throwsIff<!>(x: Int): Int {
    postconditions<Int> { !(x < 0) }
    throwsOnlyIf { x < 0 }
    if (x < 0) throw IllegalArgumentException()
    return x
}

fun <!VIPER_TEXT!>caughtStillChecked<!>(x: Int): Int {
    throwsOnlyIf { false }
    try {
        if (x < 0) <!THROWS_CONDITION_NOT_ESTABLISHED!>throw <!THROWS_CONDITION_NOT_ESTABLISHED!>IllegalArgumentException()<!><!>
    } catch (e: Throwable) {
        return 0
    }
    return x
}

open <!VIPER_TEXT!>class Reader<!> {
    open fun <!VIPER_TEXT!>read<!>(x: Int): Int {
        throwsOnlyIf { x < 0 }
        if (x < 0) throw IllegalArgumentException()
        return x
    }
}

<!VIPER_TEXT!>class Strict<!> : Reader() {
    override fun <!VIPER_TEXT!>read<!>(x: Int): Int {
        throwsOnlyIf { x < -5 }
        if (x < -5) throw IllegalArgumentException()
        return x
    }
}

<!VIPER_TEXT!>class Loose<!> : Reader() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>read<!>(x: Int): Int {
        throwsOnlyIf { x < 1 }
        if (x < 1) throw IllegalArgumentException()
        return x
    }
}

<!VIPER_TEXT!>class Inheriting<!> : Reader() {
    override fun <!VIPER_TEXT!>read<!>(x: Int): Int {
        if (x < 0) throw IllegalArgumentException()
        return x
    }
}

<!VIPER_TEXT!>class Unclaused<!> : Reader() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>read<!>(x: Int): Int {
        postconditions<Int> { true }
        return x
    }
}

fun <!VIPER_TEXT!>callerThroughBase<!>(r: Reader, x: Int): Int {
    throwsOnlyIf { x < 0 }
    return r.read(x)
}
