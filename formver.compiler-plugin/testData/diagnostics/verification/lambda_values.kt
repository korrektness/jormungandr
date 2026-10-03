// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

fun <!VIPER_TEXT!>takesLambda<!>(f: () -> Int): Int = 0

// The stdlib scope functions have no body visible to the plugin, so their lambda is a value.
fun runOnUnique(): Int {
    val v: @Unique Counter = Counter(0)
    return v.run <!UNSUPPORTED_FEATURE!>{ x }<!>
}

fun applyOnUnique() {
    val v: @Unique Counter = Counter(0)
    v.apply <!UNSUPPORTED_FEATURE!>{ x = 1 }<!>
}

fun alsoOnUnique() {
    val v: @Unique Counter = Counter(0)
    v.also <!UNSUPPORTED_FEATURE!>{ it.x = 1 }<!>
}

fun runOnInt(): Int = 1.run <!UNSUPPORTED_FEATURE!>{ this + 1 }<!>

fun passedToNonInline(): Int = takesLambda <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun storedInLocal(): Int {
    val f = <!UNSUPPORTED_FEATURE!>{ 1 }<!>
    return f()
}

fun returned(): () -> Int = <!UNSUPPORTED_FEATURE!>{ 1 }<!>

fun <!VERIFICATION_SKIPPED!>invokedDirectly<!>(): Int = <!UNSUPPORTED_FEATURE!>{ 1 }()<!>
