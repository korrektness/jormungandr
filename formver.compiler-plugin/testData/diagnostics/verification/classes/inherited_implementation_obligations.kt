// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

interface RuntimeTally {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int
    @SpecOf("inc") @Unique @Borrowed fun incSpec() {
        postconditions<Unit> { count() == old(count()) + 1 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>()
}

open class CountingBase(var n: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() { n++ }
}
<!VIPER_TEXT!>class InheritedCounting<!> : CountingBase(0), RuntimeTally

open class IdleBase(var n: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {}
}
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InheritedIdle<!> : IdleBase(0), RuntimeTally<!>

open class BoundBase(var n: Int, var other: Int) : RuntimeTally {
    @Pure @Unique override fun <!VIPER_TEXT!>count<!>(): Int = n
    @NeverVerify @Unique @Borrowed override fun <!VIPER_TEXT!>inc<!>() { n++ }
}
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class ChangedBinding<!> : BoundBase(0, 5) {
    @Pure @Unique override fun <!VIPER_TEXT!>count<!>(): Int = other
}<!>
<!VIPER_TEXT!>class SameBinding<!> : BoundBase(0, 5) {
    @Pure @Unique override fun <!VIPER_TEXT!>count<!>(): Int = n
}

interface NonnegativeTally : RuntimeTally {
    @Law @Pure @Unique fun nonnegative(): Boolean = count() >= 0
}
abstract class AbstractTally : NonnegativeTally
abstract class AbstractMiddle : AbstractTally()
<!VIPER_TEXT!>class ConcreteTally<!>(var n: Int) : AbstractMiddle() {
    init { require(n >= 0) }
    @Law @Pure @Unique fun representation(): Boolean = n >= 0
    @Pure @Unique override fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed override fun <!VIPER_TEXT!>inc<!>() { n++ }
}

interface StrongerTally {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int
    @SpecOf("inc") @Unique @Borrowed fun strongerSpec() {
        postconditions<Unit> { count() == old(count()) + 2 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>()
}
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InheritedStronger<!> : CountingBase(0), StrongerTally<!>

open class ContractBase(var n: Int) {
    @Unique @Borrowed fun <!VIPER_TEXT!>add<!>(amount: Int): Int {
        preconditions { amount > 0 }
        postconditions<Int> { result -> n == old(n) + amount && result == n }
        n += amount
        return n
    }
}
<!VIPER_TEXT!>class ContractLeaf<!> : ContractBase(0)

abstract class AbstractCounting : CountingBase(0), RuntimeTally
<!VIPER_TEXT!>class ConcreteCounting<!> : AbstractCounting()
open <!VIPER_TEXT!>class OpenCounting<!> : CountingBase(0), RuntimeTally

interface LeftTally : RuntimeTally
interface RightTally : RuntimeTally
<!VIPER_TEXT!>class DiamondCounting<!> : CountingBase(0), LeftTally, RightTally

open class RestrictedBase(var n: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {
        preconditions { count() > 0 }
        postconditions<Unit> { count() == old(count()) + 1 }
        n++
    }
}
<!OVERRIDE_NOT_REFINING, VIPER_TEXT!>class InheritedRestricted<!> : RestrictedBase(0), RuntimeTally

open class HonestIdleBase(var n: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {
        postconditions<Unit> { count() == old(count()) }
    }
}
<!OVERRIDE_NOT_REFINING, VIPER_TEXT!>class InheritedHonestIdle<!> : HonestIdleBase(0), RuntimeTally

open class SpecifiedCountingBase(var n: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = n
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {
        postconditions<Unit> { count() == old(count()) + 1 }
        n++
    }
}
<!VIPER_TEXT!>class InheritedSpecifiedCounting<!> : SpecifiedCountingBase(0), RuntimeTally
