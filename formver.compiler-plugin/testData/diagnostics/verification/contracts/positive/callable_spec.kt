// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface CounterSpec {
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
    @GhostObserver @Pure fun <!VIPER_TEXT!>inc<!>(): Int = ghostObserver()

    @SpecOf("invoke") @Unique @Borrowed
    fun invokeSpec() {
        preconditions {
            -1000000000 <= value() && value() <= 1000000000
            -100000000 <= inc() && inc() <= 100000000
        }
        postconditions<Int> { result ->
            value() == old(value()) + old(inc())
            inc() == old(inc())
            result == value()
        }
    }

    @Unique @Borrowed
    operator fun <!VIPER_TEXT!>invoke<!>(): Int
}

fun <!VIPER_TEXT!>callTwice<!>(f: @Unique @Borrowed CounterSpec): Int {
    preconditions {
        -100000000 <= f.value() && f.value() <= 100000000
        -100000000 <= f.inc() && f.inc() <= 100000000
    }
    postconditions<Int> { result ->
        f.value() == old(f.value()) + 2 * old(f.inc())
        f.inc() == old(f.inc())
        result == f.value()
    }
    f()
    return f()
}

fun <!VIPER_TEXT!>applyTwice<!>(f: @Unique CounterSpec): Int {
    preconditions {
        -100000000 <= f.value() && f.value() <= 100000000
        -100000000 <= f.inc() && f.inc() <= 100000000
    }
    postconditions<Int> { result -> result == old(f.value()) + 2 * old(f.inc()) }
    f()
    return f()
}
