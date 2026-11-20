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
