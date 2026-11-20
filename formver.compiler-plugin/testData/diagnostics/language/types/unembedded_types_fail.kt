// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface A
interface B
interface C

fun <!VIPER_TEXT!>smartCastIntersection<!>(x: A) {
    if (x is B) {
        val y = x
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>y is C<!>)
    }
}

fun <T> <!VIPER_TEXT!>definitelyNonNull<!>(x: T & Any) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x is String<!>)
}

interface NumberBox<T : Number> {
    val value: T
}

fun <!VIPER_TEXT!>capturedStar<!>(b: NumberBox<*>) {
    val v = b.value
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v is Int<!>)
}

fun <!VIPER_TEXT!>platformCollection<!>() {
    val l = <!OPAQUE_CALL!>java.util.Collections.emptyList<String>()<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>l != null<!>)
}

<!VIPER_TEXT!>class Factory<!> {
    @NeverVerify
    private fun <!VIPER_TEXT!>make<!>() = object : A, B {}

    fun <!VIPER_TEXT!>anonymousObject<!>() {
        val o = make()
        verify(<!IMPOSSIBLE_IS_CHECK_WARNING, VERIFICATION_CONDITION_NOT_ESTABLISHED!>o is C<!>)
    }
}
