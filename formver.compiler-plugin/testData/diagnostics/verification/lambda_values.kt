// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

fun <!VIPER_TEXT!>takesLambda<!>(f: () -> Int): Int = 0

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>runOnUnique<!>(): Int {
    val v: @Unique Counter = Counter(0)
    return <!UNSUPPORTED_OWNERSHIP!>v.run { x }<!>
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>applyOnUnique<!>() {
    val v: @Unique Counter = Counter(0)
    <!UNSUPPORTED_OWNERSHIP!>v.apply { x = 1 }<!>
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>alsoOnUnique<!>() {
    val v: @Unique Counter = Counter(0)
    <!UNSUPPORTED_OWNERSHIP!>v.also { it.x = 1 }<!>
}

fun <!VIPER_TEXT!>runOnInt<!>(): Int = 1.run { this + 1 }

fun <!VERIFICATION_SKIPPED!>passedToNonInline<!>(): Int = takesLambda <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun <!VERIFICATION_SKIPPED!>storedInLocal<!>(): Int {
    val f = <!UNSUPPORTED_FEATURE!>{ 1 }<!>
    return f()
}

fun <!VERIFICATION_SKIPPED!>returned<!>(): () -> Int = <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun <!VERIFICATION_SKIPPED!>invokedDirectly<!>(): Int = <!UNSUPPORTED_FEATURE!>{ 1 }()<!>
