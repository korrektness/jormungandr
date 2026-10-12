// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

interface Reading {
    @Pure @Unique fun <!VIPER_TEXT!>read<!>(): Int
}

open <!VIPER_TEXT!>class LawBase<!>(var n: Int) : Reading {
    init { require(n >= 0) }
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = n
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
    @Unique @Borrowed fun <!VIPER_TEXT!>increment<!>() { n++ }
    @Unique @Borrowed fun <!VIPER_TEXT!>copyObservation<!>() { n = read() }
}

<!VIPER_TEXT!>class LawDerived<!>(n: Int, var m: Int) : LawBase(n) {
    init { require(m >= 0) }
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = m
    @Law @Pure @Unique fun ownNonnegative(): Boolean = read() >= 0
    @Unique @Borrowed fun <!VIPER_TEXT!>incrementOther<!>() { m++ }
    <!VIPER_VERIFICATION_ERROR!>@Unique @Borrowed fun <!VIPER_TEXT!>breakBase<!>() { n = -1 }<!>
    @Unique @Borrowed fun <!VIPER_TEXT!>inspect<!>() {
        verify(n >= 0)
        verify(m >= 0)
        verify(read() == m)
    }
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class NegativeDispatch<!>(n: Int) : LawBase(n) {
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = -1
}<!>

open class PlainBase(@LawBound var n: Int = 3) : Reading {
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = nested()
    @Pure @Unique fun <!VIPER_TEXT!>nested<!>(): Int = n
}
<!VIPER_TEXT!>class InheritedLaw<!>(n: Int) : PlainBase(n) {
    init { require(n >= 0) }
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
    @Unique @Borrowed fun <!VIPER_TEXT!>increment<!>() { n++ }
    <!VIPER_VERIFICATION_ERROR!>@Unique @Borrowed fun <!VIPER_TEXT!>breakInvariant<!>() { n = -1 }<!>
}

<!VIPER_TEXT!>class DefaultInheritedLaw<!> : PlainBase() {
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}
<!VIPER_TEXT!>class ComputedInheritedLaw<!>(n: Int) : PlainBase(n + 1) {
    init { require(n >= -1) }
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}
open <!VIPER_TEXT!>class StableLawBase<!>(var n: Int) : Reading {
    init { require(n >= 0) }
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = n
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}
<!VIPER_TEXT!>class StableOverride<!>(n: Int) : StableLawBase(n) {
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = -1
}
fun <!VIPER_TEXT!>stableOverride<!>() {
    val value: @Unique StableOverride = StableOverride(1)
    verify(value.read() == -1)
    verify(value.n >= 0)
}
fun <!VIPER_TEXT!>loopWithInheritedLaw<!>(value: @Unique @Borrowed InheritedLaw) {
    var i = 0
    while (i < 2) {
        loopInvariants { i >= 0 && i <= 2 }
        value.n++
        i++
    }
    verify(value.n >= 0)
}

fun <!VIPER_TEXT!>validConstruction<!>() {
    val derived: @Unique LawDerived = LawDerived(1, 2)
    derived.increment()
    derived.incrementOther()
    derived.inspect()
    val inherited: @Unique InheritedLaw = InheritedLaw(3)
    inherited.increment()
    verify(inherited.n >= 0)
    val defaultValue: @Unique DefaultInheritedLaw = DefaultInheritedLaw()
    verify(defaultValue.n >= 0)
    val computed: @Unique ComputedInheritedLaw = ComputedInheritedLaw(0)
    verify(computed.n >= 0)
}

open class ChangedInitializerBase(@LawBound var n: Int) : Reading {
    init { n = 1 }
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = n
}
<!VIPER_TEXT!>class ChangedInitializerLaw<!>(n: Int) : ChangedInitializerBase(n) {
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}
<!VIPER_TEXT!>class UnrelatedInitializerLaw<!>(n: Int, val m: Int) : ChangedInitializerBase(n) {
    init { require(m >= 0) }
    @Law @Pure fun nonnegative(): Boolean = m >= 0
}
fun <!VIPER_TEXT!>changedInitializerConstruction<!>() {
    val value: @Unique ChangedInitializerLaw = ChangedInitializerLaw(0)
}
fun <!VIPER_TEXT!>unrelatedInitializerConstruction<!>() {
    val value: @Unique UnrelatedInitializerLaw = UnrelatedInitializerLaw(0, 1)
    verify(value.m >= 0)
}

@Pure fun <!VIPER_TEXT!>first<!>(data: @Unique IntArray): Int = if (data.size > 0) data[0] else 0
open class ChangedOwnedStateBase(val data: @Unique IntArray) : Reading {
    init { if (data.size > 0) data[0] = 1 }
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(): Int = first(data)
}
<!VIPER_TEXT!>class ChangedOwnedStateLaw<!>(data: @Unique IntArray) : ChangedOwnedStateBase(data) {
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}
fun <!VIPER_TEXT!>changedOwnedStateConstruction<!>(data: @Unique IntArray) {
    val value: @Unique ChangedOwnedStateLaw = ChangedOwnedStateLaw(data)
}
