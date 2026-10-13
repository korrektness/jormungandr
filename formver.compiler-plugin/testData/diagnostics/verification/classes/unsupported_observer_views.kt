// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

interface Tally {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int
    @SpecOf("inc") @Unique @Borrowed fun incSpec() {
        preconditions { count() < 2147483647 }
        postconditions<Unit> { count() == old(count()) + 1 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>()
}
open <!VIPER_TEXT!>class TallyBase<!>(var v: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = v
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {}
}
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class InheritedTally<!> : TallyBase(0), Tally<!>
fun <!VIPER_TEXT!>tallyClient<!>(t: @Unique @Borrowed Tally) {
    preconditions { t.count() < 2147483647 }
    postconditions<Unit> { t.count() == old(t.count()) + 1 }
    val before = t.count()
    t.inc()
    verify(t.count() == before + 1)
}
fun <!VIPER_TEXT!>useInherited<!>(t: @Unique @Borrowed InheritedTally) {
    preconditions { t.count() < 2147483647 }
    val before = t.count()
    tallyClient(t)
    verify(t.count() == before + 1)
}

interface Level {
    @Pure @Unique fun <!VIPER_TEXT!>level<!>(): Int
    @Law @Pure @Unique fun nonnegative(): Boolean = level() >= 0
}
abstract class AbstractLevel : Level {
    @Pure @Unique abstract override fun <!VIPER_TEXT!>level<!>(): Int
}
<!VIPER_TEXT!>class ConcreteLevel<!>(var l: Int) : AbstractLevel() {
    init { require(l >= 0) }
    @Law @Pure @Unique fun representation(): Boolean = l >= 0
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = l
}
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class NegativeLevel<!> : AbstractLevel() {
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = -1
}<!>
fun <!VIPER_TEXT!>levelClient<!>(l: @Unique @Borrowed Level) {
    postconditions<Unit> { l.level() == old(l.level()) }
    verify(l.level() >= 0)
}
fun <!VIPER_TEXT!>upcastAbstract<!>(a: @Unique @Borrowed AbstractLevel) {
    val before = a.level()
    levelClient(a)
    verify(a.level() == before)
    verify(a.level() >= 0)
}
fun <!VIPER_TEXT!>upcastConcrete<!>(c: @Unique @Borrowed ConcreteLevel) {
    levelClient(c)
    verify(c.level() >= 0)
}
