// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>compareSign<!>(a: Int, b: Int): Int {
    postconditions<Int> {
        (a < b) implies (it == -1)
        (a == b) implies (it == 0)
        (a > b) implies (it == 1)
    }
    return a.compareTo(b)
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>compareIsNotDifference<!>(a: Int): Int {
    preconditions { a == 3 }
    postconditions<Int> { it == 2 }
    return a.compareTo(1)
}<!>

@Pure
fun <!VIPER_TEXT!>pureCompare<!>(a: Int, b: Int): Int = a.compareTo(b)

fun <!VIPER_TEXT!>compareExtremes<!>(a: Int, b: Int) {
    preconditions { a == -2147483648; b == 2147483647 }
    verify(a.compareTo(b) == -1, b.compareTo(a) == 1, pureCompare(a, b) == -1)
}
