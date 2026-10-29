// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Pure fun <!VIPER_TEXT!>nonnegative<!>(value: Int): Boolean = value >= 0
@Pure fun <!VIPER_TEXT!>adjusted<!>(value: Int): Int {
    preconditions { value < 2147483647 }
    return value + 1
}

<!VIPER_TEXT!>class Positive<!>(start: Int) {
    var value: Int = if (start < 2147483647) adjusted(start) else start
    init { require(start >= 0 && start < 2147483647) }
    @Law @Pure fun invariant(): Boolean = value > 0 && nonnegative(value)

    @Unique @Borrowed
    fun <!VIPER_TEXT!>increment<!>() {
        preconditions { value < 2147483647 }
        verify(value > 0)
        value++
        refute(false)
    }

    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed
    fun <!VIPER_TEXT!>reset<!>() {
        value = 0
    }<!>

    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique
    fun <!VIPER_TEXT!>consumeBroken<!>() {
        value = -1
    }<!>

    @Unique @Borrowed
    fun <!VIPER_TEXT!>restore<!>(early: Boolean) {
        verify(value > 0)
        value = 0
        value = 1
        if (early) {
            return
        }
        value = 2
        refute(false)
    }
}

fun <!VIPER_TEXT!>createPositive<!>(start: Int) {
    if (start >= 0 && start < 2147483647) {
        val p: @Unique Positive = Positive(start)
        verify(p.value == start + 1)
        verify(p.value > 0)
    }
}

<!VIPER_TEXT!>class NestedObservation<!>(val value: Int) {
    init { require(value >= 0) }
    @Pure @Unique fun <!VIPER_TEXT!>observed<!>(): Int = value
    @Pure @Unique fun <!VIPER_TEXT!>nested<!>(): Int = observed()
    @Law @Pure @Unique fun invariant(): Boolean = nested() >= 0
}

fun <!VIPER_TEXT!>createNestedObservation<!>(value: Int) {
    if (value >= 0) {
        val good: @Unique NestedObservation = NestedObservation(value)
        verify(good.value >= 0)
    }
}

<!VIPER_TEXT!>class Swapped<!>(a: Int, b: Int) {
    val first = b
    val second = a
    init { require(b > a) }
    @Law @Pure fun invariant(): Boolean = first > second
}

fun <!VIPER_TEXT!>createSwapped<!>() {
    val good: @Unique Swapped = Swapped(1, 2)
    verify(good.first == 2 && good.second == 1)
}
