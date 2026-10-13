// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

fun <!VIPER_TEXT!>takesLambda<!>(f: () -> Int): Int = 0

// The stdlib scope functions have no body visible to the plugin, so their lambda is a value.
fun <!VERIFICATION_SKIPPED!>runOnUnique<!>(): Int {
    val v: @Unique Counter = Counter(0)
    return v.run <!UNSUPPORTED_FEATURE!>{ x }<!>
}

fun <!VERIFICATION_SKIPPED!>applyOnUnique<!>() {
    val v: @Unique Counter = Counter(0)
    v.apply <!UNSUPPORTED_FEATURE!>{ x = 1 }<!>
}

fun <!VERIFICATION_SKIPPED!>alsoOnUnique<!>() {
    val v: @Unique Counter = Counter(0)
    v.also <!UNSUPPORTED_FEATURE!>{ it.x = 1 }<!>
}

fun <!VERIFICATION_SKIPPED!>runOnInt<!>(): Int = 1.run <!UNSUPPORTED_FEATURE!>{ this + 1 }<!>

fun <!VERIFICATION_SKIPPED!>passedToNonInline<!>(): Int = takesLambda <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun <!VERIFICATION_SKIPPED!>storedInLocal<!>(): Int {
    val f = <!UNSUPPORTED_FEATURE!>{ 1 }<!>
    return f()
}

fun <!VERIFICATION_SKIPPED!>returned<!>(): () -> Int = <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun <!VERIFICATION_SKIPPED!>invokedDirectly<!>(): Int = <!UNSUPPORTED_FEATURE!>{ 1 }()<!>
