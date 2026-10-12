// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface IncCounterSpec {
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
    @GhostObserver @Pure fun <!VIPER_TEXT!>inc<!>(): Int = ghostObserver()

    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke")
    fun invokeSpec() {
        postconditions<Int> { result ->
            value() == old(value()) + old(inc())
            inc() == old(inc())
            result == value()
            result > old(value())
        }
    }
}

fun <!VIPER_TEXT!>makeCounter<!>(step: Int): @Unique IncCounterSpec {
    postconditions<IncCounterSpec> { result ->
        result.value() == 0
        result.inc() == if (step <= 0) 1 else step
    }
    var n = 0
    return if (step <= 0) IncCounterSpec {
        exposes(IncCounterSpec::class) { value() == n; inc() == 1 }
        n += 1
        n
    } else IncCounterSpec {
        exposes(IncCounterSpec::class) { value() == n; inc() == step }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>callTwice<!>(f: @Unique @Borrowed IncCounterSpec): Int {
    postconditions<Int> { result ->
        f.value() == old(f.value()) + 2 * old(f.inc())
        f.inc() == old(f.inc())
        result == f.value()
    }
    f()
    return f()
}

fun <!VIPER_TEXT!>applyTwice<!>(f: @Unique IncCounterSpec): Int {
    postconditions<Int> { result -> result == old(f.value()) + 2 * old(f.inc()) }
    return callTwice(f)
}

fun <!VIPER_TEXT!>client<!>(step: Int) {
    val f: @Unique IncCounterSpec = makeCounter(step)
    val r1 = f()
    val r2 = f()
    verify(r1 == if (step <= 0) 1 else step)
    verify(r2 == 2 * r1)
}

fun <!VIPER_TEXT!>borrowedClient<!>(step: Int) {
    val f: @Unique IncCounterSpec = makeCounter(step)
    val r2 = callTwice(f)
    verify(r2 == 2 * (if (step <= 0) 1 else step))
    val r3 = f()
    verify(r3 == 3 * (if (step <= 0) 1 else step))
}

fun <!VIPER_TEXT!>consumingClient<!>(step: Int) {
    val r2 = applyTwice(makeCounter(step))
    verify(r2 == 2 * (if (step <= 0) 1 else step))
}

fun <!VIPER_TEXT!>demandsMore<!>(): @Unique IncCounterSpec {
    var n = 0
    return IncCounterSpec <!OVERRIDE_NOT_REFINING!>{
        exposes(IncCounterSpec::class) { value() == n; inc() == 1 }
        preconditions { n > 0 }
        postconditions<Int> { result -> n == old(n) + 1 && result == n }
        n += 1
        n
    }<!>
}

fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>deliversLess<!>(): @Unique IncCounterSpec {
    var n = 0
    return IncCounterSpec {
        exposes(IncCounterSpec::class) { value() == n; inc() == 1 }
        postconditions<Int> { result -> result == n && n == old(n) }
        n
    }
}

fun <!VIPER_TEXT!>exactIncrementAfterJoin<!>(step: Int) {
    val f: @Unique IncCounterSpec = makeCounter(step)
    val r1 = f()
    verify(<!VIPER_VERIFICATION_ERROR!>r1 == step<!>)
}
