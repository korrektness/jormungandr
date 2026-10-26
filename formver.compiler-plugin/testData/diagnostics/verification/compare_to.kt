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

fun <!VIPER_TEXT!>compareIsNotDifference<!>(a: Int): Int {
    preconditions { a == 3 }
    postconditions<Int> { it == 2 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return a.compareTo(1)<!>
}

@Pure
fun <!VIPER_TEXT!>pureCompare<!>(a: Int, b: Int): Int = a.compareTo(b)

fun <!VIPER_TEXT!>compareExtremes<!>(a: Int, b: Int) {
    preconditions { a == -2147483648; b == 2147483647 }
    verify(a.compareTo(b) == -1, b.compareTo(a) == 1, pureCompare(a, b) == -1)
}

@Pure
operator fun Int?.<!VIPER_TEXT!>compareTo<!>(other: Int?): Int = when {
    this == null -> if (other == null) 0 else -1
    other == null -> 1
    else -> this.compareTo(other)
}

fun <!VIPER_TEXT!>nullableExtensionComparison<!>(a: Int?, b: Int?) {
    preconditions { a == null; b == 1 }
    verify(a < b, a <= b, b > a, b >= a, a <= a, b >= b)
}

fun <!VIPER_TEXT!>extensionComparisonEvaluatesArgumentsOnce<!>() {
    var count = 0
    val less = run { count = count + 1; null as Int? } < run { count = count + 1; 1 as Int? }
    verify(less, count == 2)
}
