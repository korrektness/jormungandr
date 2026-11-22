// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface A
interface B

fun <!VIPER_TEXT!>smartCastIntersection<!>(x: A): B? {
    if (x is B) {
        val y = x
        return y
    }
    return null
}

fun <T> <!VIPER_TEXT!>definitelyNonNull<!>(x: T & Any) {
    verify(<!SENSELESS_COMPARISON!>x != null<!>)
}

interface NumberBox<T : Number> {
    val value: T
}

fun <!VIPER_TEXT!>capturedStar<!>(b: NumberBox<*>) {
    val v = b.value
    verify(<!USELESS_IS_CHECK!>v is Number<!>)
}

fun <!VIPER_TEXT!>platformCollection<!>() {
    val l = <!OPAQUE_CALL!>java.util.Collections.emptyList<String>()<!>
    verify(l == null || <!USELESS_IS_CHECK!>l is Collection<*><!>)
}

<!VIPER_TEXT!>class Factory<!> {
    @NeverVerify
    private fun <!VIPER_TEXT!>make<!>() = object : A, B {}

    fun <!VIPER_TEXT!>anonymousObject<!>() {
        val o = make()
        verify(<!USELESS_IS_CHECK!>o is A<!>, <!USELESS_IS_CHECK!>o is B<!>)
    }
}

open <!VIPER_TEXT!>class Base<!> {
    fun <!VIPER_TEXT!>go<!>(): Int = 1
}

<!VIPER_TEXT!>class BaseHolder<!><T : Base>(val item: T) {
    fun <!VIPER_TEXT!>field<!>(): Int = item.go()
}

fun <T : Base> <!VIPER_TEXT!>parameterBound<!>(x: T): Int = x.go()

fun <T : Base?> <!VIPER_TEXT!>nullableBound<!>(x: T): Int = if (x != null) x.go() else 0

fun <T> <!VIPER_TEXT!>multipleBounds<!>(x: T) where T : A, T : B {
    verify(<!USELESS_IS_CHECK!>x is A<!>, <!USELESS_IS_CHECK!>x is B<!>)
}

fun <U : Base, T : U> <!VIPER_TEXT!>transitiveBound<!>(x: T): Int = x.go()

inline fun <reified E : Base> <!VIPER_TEXT!>reifiedSmartCast<!>(x: Any): Int = if (x is E) x.go() else 0
