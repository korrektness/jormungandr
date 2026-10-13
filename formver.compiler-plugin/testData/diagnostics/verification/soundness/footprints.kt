// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Family transfers and observer views hand one object between a class and an interface presentation (docs/soundness.md, M5).

interface Gauge {
    @Pure @Unique fun <!VIPER_TEXT!>reading<!>(): Int
    @Unique @Borrowed fun <!VIPER_TEXT!>reset<!>()
}

<!VIPER_TEXT!>class Dial<!>(var level: Int, var spare: Int) : Gauge {
    @Pure @Unique override fun <!VIPER_TEXT!>reading<!>(): Int = level
    @Pure @Unique fun <!VIPER_TEXT!>hidden<!>(): Int = spare
    @Unique @Borrowed override fun <!VIPER_TEXT!>reset<!>() { level = 0 }
}

// The interface view writes `level`, which the class observer reads; the restore must not carry the old reading back.
fun <!VIPER_TEXT!>interfaceWriteThenRestore<!>(d: @Unique @Borrowed Dial) {
    d.level = 5
    (d as Gauge).reset()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.reading() == 5<!>)
}

@NeverConvert
fun resetGauge(g: @Unique @Borrowed Gauge) {
    g.reset()
}

// As above, with the interface write hidden in a callee that has no postcondition.
fun <!VIPER_TEXT!>calleeWriteThenRestore<!>(d: @Unique @Borrowed Dial) {
    val before = d.reading()
    resetGauge(d)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.reading() == before<!>)
}

// A callee whose contract only mentions `reading` must not be read as preserving `spare`.
@NeverConvert
fun keepReading(g: @Unique @Borrowed Gauge) {
    postconditions<Unit> { g.reading() == old(g.reading()) }
}

fun <!VIPER_TEXT!>classObserverOutsideInterface<!>(d: @Unique @Borrowed Dial) {
    val reading = d.reading()
    val hidden = d.hidden()
    keepReading(d)
    verify(d.reading() == reading)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.hidden() == hidden<!>)
}

// As above, reading the field directly rather than through a class observer.
fun <!VIPER_TEXT!>classFieldOutsideInterface<!>(d: @Unique @Borrowed Dial) {
    val reading = d.reading()
    d.spare = 3
    keepReading(d)
    verify(d.level == reading)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.spare == 3<!>)
}

interface Probe {
    @Pure @Unique fun <!VIPER_TEXT!>at<!>(i: Int): Int
    @SpecOf("at") @Unique fun atSpec(i: Int) {
        preconditions { i >= 0 }
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>shift<!>()
    @SpecOf("shift") @Unique @Borrowed fun shiftSpec() {
        postconditions<Unit> { at(0) == old(at(0)) }
    }
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
<!VIPER_TEXT!>class Line<!>(var base: Int, var below: Int) : Probe {
    @Pure @Unique override fun <!VIPER_TEXT!>at<!>(j: Int): Int {
        preconditions { j >= -1 }
        return if (j < 0) below else base
    }

    @Unique @Borrowed override fun <!VIPER_TEXT!>shift<!>() { below = 0 }
}

// `at(-1)` lies outside the interface observer's domain, so the restore relates nothing there.
fun <!VIPER_TEXT!>restoreOutsideDomain<!>(l: @Unique @Borrowed Line) {
    val before = l.at(-1)
    (l as Probe).shift()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>l.at(-1) == before<!>)
}

// Inside the interface domain the restore carries the preserved observation back.
fun <!VIPER_TEXT!>restoreInsideDomain<!>(l: @Unique @Borrowed Line) {
    val before = l.at(0)
    (l as Probe).shift()
    verify(l.at(0) == before)
    refute(false)
}

interface Shown {
    @Pure @Unique fun <!VIPER_TEXT!>shown<!>(): Long
}

open <!VIPER_TEXT!>class Meter<!>(var raw: Int) : Shown {
    @Pure @Unique override fun <!VIPER_TEXT!>shown<!>(): Long = raw.toLong()
}

<!VIPER_TEXT!>class DoubledMeter<!>(raw: Int) : Meter(raw) {
    @Pure @Unique override fun <!VIPER_TEXT!>shown<!>(): Long = raw.toLong() * 2L
}

// A class view hides subclass dispatch; the exact binding applies only to a runtime `Meter`.
fun <!VIPER_TEXT!>openObserverView<!>(m: @Unique @Borrowed Meter) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>m.shown() == m.raw.toLong()<!>)
}

// Sound to prove, but the constructor states only a runtime subtype, so the exact binding never applies.
fun <!VIPER_TEXT!>exactMeter<!>() {
    val m: @Unique Meter = Meter(3)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>m.shown() == 3L<!>)
}

// The class view of a `DoubledMeter` must not use the `Meter` body.
fun <!VIPER_TEXT!>subclassAsMeter<!>() {
    val m: @Unique Meter = DoubledMeter(3)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>m.shown() == 3L<!>)
}

// The interface binding of a `DoubledMeter` held as `Meter` is the subclass body.
fun <!VIPER_TEXT!>subclassThroughInterface<!>() {
    val m: @Unique Meter = DoubledMeter(3)
    val s: @Unique Shown = m
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.shown() == 3L<!>)
}
