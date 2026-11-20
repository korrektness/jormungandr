// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*
import kotlin.reflect.KProperty

<!VIPER_TEXT!>class Positive<!>(val n: Int) {
    operator fun <!VIPER_TEXT!>getValue<!>(thisRef: Any?, property: KProperty<*>): Int {
        postconditions<Int> { it > 0 }
        return if (n > 0) n else 1
    }

    operator fun <!VIPER_TEXT!>setValue<!>(thisRef: Any?, property: KProperty<*>, value: Int) {
        preconditions { value > 0 }
    }
}

<!VERIFICATION_SKIPPED!>class Holder<!> {
    var <!VIPER_TEXT, VIPER_TEXT!>e<!>: Int by <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Positive(2)<!>

    fun <!VIPER_TEXT!>readMember<!>() {
        val v = e
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v > 1<!>)
    }

    fun <!VIPER_TEXT!>writeMember<!>() {
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>e = 0<!>
    }
}

fun <!VIPER_TEXT!>localDelegate<!>() {
    var x: Int by Positive(4)
    val before = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>before > 1<!>)
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x = 0<!>
}

fun <!VIPER_TEXT!>classOf<!>(a: Any, b: Any) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a::class == b::class<!>)
}

fun <!VIPER_TEXT!>classOfEvaluatesReceiver<!>(s: String?) {
    var reached = false
    try {
        val k = s!!::class
        reached = true
    } catch (e: NullPointerException) {
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>reached<!>)
}

fun <!VIPER_TEXT!>javaFieldWriteIsNotKept<!>(p: java.awt.Point) {
    p.x = 3
    val v = <!OPAQUE_CALL!>p.x<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v == 3<!>)
}

fun <!VIPER_TEXT!>javaFieldMayThrow<!>(p: java.awt.Point) {
    var reached = false
    try {
        val q = p.location
        q.x = 1
        reached = true
    } catch (e: NullPointerException) {
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>reached<!>)
}
