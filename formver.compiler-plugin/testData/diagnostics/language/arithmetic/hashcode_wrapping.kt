// FULL_JDK
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Key<!>(val a: Int, val b: Int) {
    override fun <!VIPER_TEXT!>hashCode<!>(): Int {
        var r = a
        r = 31 * r + b
        return r
    }

    fun <!VIPER_TEXT!>notHashCode<!>(): Int = <!INTEGER_OVERFLOW!>31 * a<!>

    fun <!VIPER_TEXT!>hashCode<!>(salt: Int): Int = <!INTEGER_OVERFLOW!>31 * a<!>
}

<!VIPER_TEXT!>class Divides<!>(val a: Int, val d: Int) {
    override fun <!VIPER_TEXT!>hashCode<!>(): Int = <!DIVISION_BY_ZERO, INTEGER_OVERFLOW!>a / d<!>
}
