// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface CounterSpec {
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
    @GhostObserver @Pure fun <!VIPER_TEXT!>inc<!>(): Int = ghostObserver()

    @SpecOf("invoke") @Unique @Borrowed
    fun invokeSpec() {
        postconditions<Int> { result ->
            value() == old(value()) + old(inc())
            inc() == old(inc())
            result == value()
        }
    }

    @Unique @Borrowed
    operator fun <!VIPER_TEXT!>invoke<!>(): Int
}

@AlwaysVerify
fun <!VIPER_TEXT!>callTwice<!>(f: @Unique @Borrowed CounterSpec): Int {
    postconditions<Int> { result ->
        f.value() == old(f.value()) + 2 * old(f.inc())
        f.inc() == old(f.inc())
        result == f.value()
    }
    f()
    return f()
}

@AlwaysVerify
fun <!VIPER_TEXT!>applyTwice<!>(f: @Unique CounterSpec): Int {
    postconditions<Int> { result -> result == old(f.value()) + 2 * old(f.inc()) }
    f()
    return f()
}
