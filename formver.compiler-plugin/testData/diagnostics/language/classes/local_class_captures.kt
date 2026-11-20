// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>localClassProperty<!>(): Int {
    <!VIPER_TEXT!>class L<!>(val x: Int)
    val l = L(1)
    verify(l.x == 1)
    return l.x
}

fun <!VIPER_TEXT!>sameNameElsewhere<!>(): Boolean {
    <!VIPER_TEXT!>class L<!>(val y: Boolean)
    return L(true).y
}

fun <!VIPER_TEXT!>capturedInMember<!>(n: Int): Int {
    preconditions { n > 0 }
    <!VIPER_TEXT!>class Box<!>(val x: Int) {
        fun <!VIPER_TEXT!>scaled<!>(): Int {
            verify(n > 0)
            return x
        }
    }
    return Box(n).scaled()
}

fun <!VIPER_TEXT!>capturedVarInMember<!>(): Int {
    var total = 0
    <!VIPER_TEXT!>class Adder<!> {
        fun <!VIPER_TEXT!>add<!>() { total = 3 }
    }
    Adder().add()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>total == 0<!>)
    return total
}

fun <!VIPER_TEXT!>capturedInInitializer<!>(n: Int): Int {
    <!VIPER_TEXT!>class Offset<!> {
        val v = n
    }
    val o = Offset()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>o.v == n<!>)
    return o.v
}

fun <!VERIFICATION_SKIPPED!>twoLocalClassesWithOneName<!>(b: Boolean): Int {
    if (b) {
        <!VIPER_TEXT!>class Same<!>(val a: Int)
        return Same(1).a
    } else {
        <!UNSUPPORTED_FEATURE!><!VIPER_TEXT!>class Same<!>(val a: Int)<!>
        return Same(2).a
    }
}

fun <!VIPER_TEXT!>capturedBySuperclass<!>(n: Int): Int {
    open <!VIPER_TEXT!>class Base<!> {
        val x = n
    }
    <!VIPER_TEXT!>class Derived<!> : Base()
    val d = Derived()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.x == n<!>)
    return d.x
}

fun <!VIPER_TEXT!>capturedBySuperclassDefault<!>(n: Int): Int {
    open <!VIPER_TEXT!>class Base<!>(val x: Int = n)
    <!VIPER_TEXT!>class Derived<!>(val m: Int) : Base() {
        val z = m + 0 * n
    }
    return Derived(1).x
}

fun <!VERIFICATION_SKIPPED!>lawReadsCapture<!>(n: Int): Int {
    <!UNSUPPORTED_FEATURE!>class Bounded(val f: Int) {
        <!UNSUPPORTED_FEATURE!>@Law @Pure fun invariant(): Boolean = f > <!INVALID_LAW!>n<!><!>
    }<!>
    return Bounded(n + 0).f
}

fun <!VERIFICATION_SKIPPED!>observerReadsCapture<!>(n: Int): Int {
    <!UNSUPPORTED_FEATURE!>class Bounded(val f: Int) {
        <!UNSUPPORTED_FEATURE!>@Pure fun <!VERIFICATION_SKIPPED!>limit<!>(): Int = n<!>
        <!UNSUPPORTED_FEATURE!>@Law @Pure fun invariant(): Boolean = f > <!INVALID_LAW!>limit()<!><!>
    }<!>
    return Bounded(n + 0).f
}
