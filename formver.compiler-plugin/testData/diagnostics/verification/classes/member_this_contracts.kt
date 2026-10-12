// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Meter(var reading: Int) {
    @Unique @Borrowed
    fun <!VIPER_TEXT!>advance<!>(by: Int) {
        preconditions { by >= 0 }
        postconditions<Unit> { reading == old(reading) + by }
        reading = reading + by
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>advanceTwice<!>() {
        postconditions<Unit> { reading == old(reading) + 3 }
        advance(1)
        advance(2)
    }

    @Unique @Pure
    fun <!VIPER_TEXT!>current<!>(): Int = reading

    @Unique
    fun <!VERIFICATION_SKIPPED!>close<!>() {
        postconditions<Unit> { <!UNSUPPORTED_OWNERSHIP!>reading<!> == 0 }
        reading = 0
    }
}

fun <!VIPER_TEXT!>advanceFromZero<!>(m: @Unique @Borrowed Meter) {
    m.reading = 0
    m.advance(1)
    m.advance(2)
    verify(m.current() == 3)
}

fun <!VIPER_TEXT!>staleAfterAdvance<!>(m: @Unique @Borrowed Meter) {
    m.reading = 0
    m.advanceTwice()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>m.reading == 0<!>)
}

open class Gauge(var level: Int) {
    @Unique @Borrowed
    open fun <!VIPER_TEXT!>tick<!>() {
        postconditions<Unit> { level == old(level) }
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>tickKeepsLevel<!>() {
        level = 3
        tick()
        verify(level == 3)
    }
}

class CountingGauge(level: Int, var ticks: Int) : Gauge(level) {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>tick<!>() {
        ticks = ticks + 1
    }
}

class DriftingGauge(level: Int) : Gauge(level) {
    @Unique @Borrowed
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>tick<!>() {
        postconditions<Unit> { true }
        level = level + 1
    }
}

open <!VIPER_TEXT!>class PlainGauge<!>(level: Int) : Gauge(level)

class FlaggingGauge(level: Int, var flagged: Boolean) : PlainGauge(level) {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>tick<!>() {
        flagged = true
    }
}

fun <!VIPER_TEXT!>staleAfterTick<!>(g: @Unique @Borrowed CountingGauge) {
    g.ticks = 0
    g.tickKeepsLevel()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>g.ticks == 0<!>)
}
