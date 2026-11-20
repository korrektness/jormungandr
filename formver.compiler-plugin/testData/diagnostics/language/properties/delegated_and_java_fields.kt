// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*
import kotlin.reflect.KProperty

<!VIPER_TEXT!>class Positive<!>(val n: Int) {
    operator fun <!VIPER_TEXT!>getValue<!>(thisRef: Any?, property: KProperty<*>): Int {
        postconditions<Int> { it > 0 }
        return if (n > 0) n else 1
    }

    operator fun <!VIPER_TEXT!>setValue<!>(thisRef: Any?, property: KProperty<*>, value: Int) {
    }
}

<!VERIFICATION_SKIPPED, VERIFICATION_SKIPPED!>class Holder<!> {
    val <!VIPER_TEXT!>d<!>: Int by Positive(1)
    var <!VIPER_TEXT, VIPER_TEXT!>e<!>: Int by Positive(2)

    fun <!VIPER_TEXT!>readMember<!>() {
        val v = d
        verify(v > 0)
    }
}

fun <!VIPER_TEXT!>readThrough<!>(h: Holder) {
    h.e = 3
    val v = h.e
    verify(v > 0)
}

fun <!VIPER_TEXT!>localDelegate<!>() {
    var x: Int by Positive(4)
    val before = x
    x = 5
    val after = x
    verify(before > 0, after > 0)
}

fun <!VIPER_TEXT!>classOf<!>(s: String) {
    val k = s::class
    val j = String::class
    verify(<!SENSELESS_COMPARISON!>k != null<!>, <!SENSELESS_COMPARISON!>j != null<!>)
}

fun <!VIPER_TEXT!>javaFieldRead<!>(p: java.awt.Point) {
    p.x = 3
    val v: Any = <!OPAQUE_CALL!>p.x<!>
    verify(v is Int)
}
