// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Pure fun <!VIPER_TEXT!>nonnegative<!>(value: Int): Boolean = value >= 0
@Pure fun <!VIPER_TEXT!>adjusted<!>(value: Int): Int = value + 1

<!VIPER_TEXT!>class Positive<!>(start: Int) {
    var value: Int = adjusted(start)
    @Law @Pure fun invariant(): Boolean = value > 0 && nonnegative(value)
}

@AlwaysVerify
fun <!VIPER_TEXT!>createPositive<!>(start: Int) {
    if (start >= 0) {
        val p: @Unique Positive = Positive(start)
        verify(p.value == start + 1)
        verify(p.value > 0)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>createBroken<!>() {
    val p: @Unique Positive = <!VIPER_VERIFICATION_ERROR!>Positive(-2)<!>
}

<!VIPER_TEXT!>class NestedObservation<!>(val value: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>observed<!>(): Int = value
    @Pure @Unique fun <!VIPER_TEXT!>nested<!>(): Int = observed()
    @Law @Pure @Unique fun invariant(): Boolean = nested() >= 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>createNestedObservation<!>(value: Int) {
    if (value >= 0) {
        val good: @Unique NestedObservation = NestedObservation(value)
        verify(good.value >= 0)
    }
    val bad: @Unique NestedObservation = <!VIPER_VERIFICATION_ERROR!>NestedObservation(-1)<!>
}

<!VIPER_TEXT!>class Swapped<!>(a: Int, b: Int) {
    val first = b
    val second = a
    @Law @Pure fun invariant(): Boolean = first > second
}

@AlwaysVerify
fun <!VIPER_TEXT!>createSwapped<!>() {
    val good: @Unique Swapped = Swapped(1, 2)
    verify(good.first == 2 && good.second == 1)
    val bad: @Unique Swapped = <!VIPER_VERIFICATION_ERROR!>Swapped(2, 1)<!>
}
