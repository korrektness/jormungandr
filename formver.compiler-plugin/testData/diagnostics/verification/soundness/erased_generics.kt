// FULL_JDK
// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

// Invariants inhaled after an unchecked cast or an erased generic substitution (docs/soundness.md, "Type invariants").
// The values are read as `Any`, where the JVM inserts no checkcast, so they really are `String`s at runtime.
// UNSOUND marks an assertion that is false at runtime but verifies: a sound verifier reports
// VERIFICATION_CONDITION_NOT_ESTABLISHED on it.

<!VIPER_TEXT!>class Cell<!><T>(val item: T)

// `as Cell<Int>` checks only the class; the element is still a `String`.
// UNSOUND: reading `item` inhales the substituted type `Int`.
fun <!VIPER_TEXT!>castCellElement<!>() {
    val any: Any = Cell<Any>("a")
    @Suppress("UNCHECKED_CAST")
    val ints = any as Cell<Int>
    val item: Any = ints.item
    verify(item is Int)
}

// UNSOUND: the `List.get` contract carries the substituted element type `Int`.
fun <!VIPER_TEXT!>castListElement<!>() {
    @Suppress("UNCHECKED_CAST")
    val l = listOf<Any>("a") as List<Int>
    val x: Any = l[0]
    verify(x is Int)
}

@Suppress("UNCHECKED_CAST")
fun <T> <!VIPER_TEXT!>coerce<!>(x: Any): T = x as T

// The substituted return type `Int` is not checked at runtime.
// UNSOUND: the call result inhales the substituted return type `Int`.
fun <!VIPER_TEXT!>uncheckedGenericReturn<!>() {
    val y: Any = coerce<Int>("a")
    verify(y is Int)
}

fun <T> <!VIPER_TEXT!>identity<!>(t: T): T = t

// The argument is checked against the substituted parameter type, so the substituted return type holds.
fun <!VIPER_TEXT!>checkedGenericReturn<!>() {
    val y: Any = identity(3)
    verify(y is Int)
    refute(false)
}

// A checked `is` narrows the runtime type for real.
fun <!VIPER_TEXT!>checkedCast<!>(x: Any) {
    if (x is Int) {
        val y: Any = x
        verify(y is Int)
    }
    refute(false)
}
