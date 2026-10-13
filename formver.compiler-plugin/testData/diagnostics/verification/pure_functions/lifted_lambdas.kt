// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface LiftedSpec {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(x: Int): Int
    @SpecOf("invoke")
    fun invokeSpec(x: Int) {
        preconditions { x > -2147483648 && x < 2147483647 && state() < 2147483647 }
        postconditions<Int> { result -> result >= x }
    }
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

fun <!VIPER_TEXT!>noCaptures<!>(): @Unique LiftedSpec = LiftedSpec { x ->
    exposes(LiftedSpec::class) { state() == 0 }
    x + 1
}

fun <!VIPER_TEXT!>withCaptures<!>(start: Int): @Unique LiftedSpec {
    var n = start
    return LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == n }
        val step = 1
        n += step
        if (n >= x) n else x
    }
}

fun <!VIPER_TEXT!>incorrectBody<!>(): @Unique LiftedSpec = LiftedSpec <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ x ->
    exposes(LiftedSpec::class) { state() == 0 }
    x - 1
}<!>

class LiftedCell(var n: Int) {
    @Unique
    fun <!VIPER_TEXT!>captureReceiver<!>(): @Unique LiftedSpec = LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == 0 }
        n = x
        n
    }
}

fun <!VIPER_TEXT!>captureObject<!>(cell: @Unique LiftedCell): @Unique LiftedSpec = LiftedSpec { x ->
    exposes(LiftedSpec::class) { state() == cell.n }
    cell.n = x
    cell.n
}

fun <!VIPER_TEXT!>captureLoop<!>(start: Int): @Unique LiftedSpec {
    var n = start
    return LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == n }
        while (n < x) {
            n += 1
        }
        if (n >= x) n else x
    }
}

fun <!VIPER_TEXT!>boundObserver<!>(): @Unique LiftedSpec {
    val f: @Unique LiftedSpec = LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == 7 }
        x
    }
    verify(f.state() == 7)
    return f
}

fun <!VIPER_TEXT!>exposedOnlyCapture<!>(k: Int): @Unique LiftedSpec {
    preconditions { k < 2147483646 }
    val f: @Unique LiftedSpec = LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == k + 1 }
        x
    }
    verify(f.state() == k + 1)
    return f
}

fun interface Counter {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke")
    fun invokeSpec() {
        preconditions { state() < 2147483647 }
        postconditions<Int> { result -> state() == old(state()) + 1 && result == state() }
    }
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

fun <!VIPER_TEXT!>countFrom<!>(start: Int) {
    preconditions { start < 2147483647 }
    var n = start
    val c: @Unique Counter = Counter {
        exposes(Counter::class) { state() == n }
        n += 1
        n
    }
    verify(c.state() == start)
    val first = c.invoke()
    verify(first == start + 1, c.state() == start + 1)
    refute(false)
}

fun <!VIPER_TEXT!>stuckCounter<!>(start: Int): @Unique Counter {
    var n = start
    return Counter <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
        exposes(Counter::class) { state() == n }
        n
    }<!>
}

@NeverConvert
fun consume(f: @Unique LiftedSpec) {}

fun <!VIPER_TEXT!>implicitSam<!>(start: Int) {
    var n = start
    consume { x ->
        exposes(LiftedSpec::class) { state() == n }
        n = x
        n
    }
}

fun <!VIPER_TEXT!>nestedSam<!>(start: Int): @Unique LiftedSpec {
    return LiftedSpec { x ->
        exposes(LiftedSpec::class) { state() == start }
        var n = start
        val inner: @Unique LiftedSpec = LiftedSpec { y ->
            exposes(LiftedSpec::class) { state() == n }
            n = y
            n
        }
        inner.invoke(x)
    }
}
