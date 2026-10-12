// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface Accumulator {
    @GhostObserver @Pure fun <!VIPER_TEXT!>total<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @SpecOf("invoke")
    fun invokeSpec(amount: Int) {
        preconditions { amount >= 0 && total() >= 0 }
        postconditions<Int> { r -> total() == old(total()) + amount && r == total() }
    }
}

class AccumulatorCell(var n: Int)

fun <!VIPER_TEXT!>ownedCapture<!>(): @Unique Accumulator {
    postconditions<Accumulator> { r -> r.total() == 0 }
    val cell: @Unique AccumulatorCell = AccumulatorCell(0)
    return Accumulator { amount ->
        exposes(Accumulator::class) { total() == cell.n }
        cell.n += amount
        cell.n
    }
}

fun <!VIPER_TEXT!>wrongParameterizedBody<!>(): @Unique Accumulator {
    var n = 0
    return Accumulator <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
        exposes(Accumulator::class) { total() == n }
        n -= amount
        n
    }<!>
}

fun <!VIPER_TEXT!>parameterClient<!>() {
    val f = ownedCapture()
    val first = f(3)
    verify(first == 3)
    val second = f(4)
    verify(second == 7)
    verify(f.total() == 7)
}

fun <!VIPER_TEXT!>badArgument<!>() {
    val f = ownedCapture()
    <!VIPER_VERIFICATION_ERROR!>f(-1)<!>
}

fun <!VIPER_TEXT!>badObservation<!>() {
    var n = -1
    val f: @Unique Accumulator = Accumulator { amount ->
        exposes(Accumulator::class) { total() == n }
        n += amount
        n
    }
    <!VIPER_VERIFICATION_ERROR!>f(1)<!>
}

class AccumulatorHolder(val action: @Unique Accumulator) {
    @Unique @Borrowed
    fun <!VIPER_TEXT!>apply<!>(amount: Int): Int {
        preconditions { amount >= 0 && action.total() >= 0 }
        postconditions<Int> { r -> action.total() == old(action.total()) + amount && r == action.total() }
        return action(amount)
    }
}

fun <!VIPER_TEXT!>fieldClient<!>() {
    val holder: @Unique AccumulatorHolder = AccumulatorHolder(ownedCapture())
    val first = holder.apply(2)
    verify(first == 2)
    val second = holder.apply(5)
    verify(second == 7)
}

fun interface AccumulatorFactory {
    @GhostObserver @Pure fun <!VIPER_TEXT!>initial<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(): @Unique Accumulator
    @SpecOf("invoke")
    fun invokeSpec() {
        postconditions<Accumulator> { r -> r.total() == initial(); initial() == old(initial()) }
    }
}

fun <!VIPER_TEXT!>nestedReturn<!>(start: Int): @Unique AccumulatorFactory {
    postconditions<AccumulatorFactory> { r -> r.initial() == start }
    return AccumulatorFactory {
        exposes(AccumulatorFactory::class) { initial() == start }
        var n = start
        Accumulator { amount ->
            exposes(Accumulator::class) { total() == n }
            n += amount
            n
        }
    }
}

fun <!VIPER_TEXT!>nestedClient<!>() {
    val factory = nestedReturn(10)
    val first = factory()
    val second = factory()
    val r1 = first(1)
    verify(r1 == 11)
    val r2 = second(2)
    verify(r2 == 12)
    verify(first.total() == 11)
}

fun interface Projection {
    @GhostObserver @Pure fun <!VIPER_TEXT!>offset<!>(): Int = ghostObserver()
    @Unique @Borrowed fun <!VIPER_TEXT!>project<!>(x: Int, y: Int): Int
    @SpecOf("project")
    fun projectSpec(x: Int, y: Int) {
        preconditions { x >= offset() && y >= x }
        postconditions<Int> { r -> r == y - x + offset(); offset() == old(offset()) }
    }
}

fun interface DerivedProjection : Projection

fun <!VIPER_TEXT!>namedOperation<!>(base: Int): @Unique DerivedProjection {
    postconditions<DerivedProjection> { r -> r.offset() == base }
    return DerivedProjection { x, y ->
        exposes(DerivedProjection::class) { offset() == base }
        y - x + base
    }
}

fun <!VIPER_TEXT!>namedOperationClient<!>() {
    val f = namedOperation(2)
    verify(f.offset() == 2)
    val r = f.project(3, 7)
    verify(r == 6)
    verify(f.offset() == 2)
}

fun interface DerivedAccumulator : Accumulator

fun <!VIPER_TEXT!>inheritedSam<!>(): @Unique DerivedAccumulator {
    postconditions<DerivedAccumulator> { r -> r.total() == 0 }
    var n = 0
    return DerivedAccumulator { amount ->
        exposes(DerivedAccumulator::class) { total() == n }
        n += amount
        n
    }
}

fun <!VIPER_TEXT!>inheritedClient<!>() {
    val f = inheritedSam()
    val r = f(6)
    verify(r == 6)
}

fun <!VIPER_TEXT!>loopLiteral<!>(count: Int) {
    preconditions { count >= 0 }
    var i = 0
    while (i < count) {
        loopInvariants { i >= 0 && i <= count }
        var n = i
        val f: @Unique Accumulator = Accumulator { amount ->
            exposes(Accumulator::class) { total() == n }
            n += amount
            n
        }
        val r = f(1)
        verify(r == i + 1)
        i += 1
    }
}

fun <!VIPER_TEXT!>applyForwarded<!>(f: @Unique @Borrowed Accumulator, amount: Int): Int {
    preconditions { amount >= 0 && f.total() >= 0 }
    postconditions<Int> { r -> f.total() == old(f.total()) + amount && r == f.total() }
    return f(amount)
}

fun <!VIPER_TEXT!>forward<!>(f: @Unique @Borrowed Accumulator, amount: Int): Int {
    preconditions { amount >= 0 && f.total() >= 0 }
    postconditions<Int> { r -> f.total() == old(f.total()) + amount && r == f.total() }
    return applyForwarded(f, amount)
}

fun <!VIPER_TEXT!>forwardingClient<!>() {
    val f = ownedCapture()
    val first = forward(f, 8)
    verify(first == 8)
    val second = f(1)
    verify(second == 9)
}
