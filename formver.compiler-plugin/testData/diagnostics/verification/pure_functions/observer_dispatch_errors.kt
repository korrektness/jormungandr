// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

interface Meter {
    @Pure @Unique fun <!VIPER_TEXT!>level<!>(): Int
    @SpecOf("bump") @Unique @Borrowed fun bumpSpec() {
        preconditions { level() < 2147483647 }
        postconditions<Unit> { level() == old(level()) + 1 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>bump<!>()
}

open class BaseMeter(var a: Int, var b: Int) : Meter {
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = a
    @Unique @Borrowed override fun <!VIPER_TEXT!>bump<!>() {
        val before = a
        if (before == 2147483647) throw IllegalArgumentException()
        a = before + 1
    }
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class DerivedMeter<!> : BaseMeter(0, 5) {
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = b
}<!>

class GoodDerivedMeter : BaseMeter(0, 5) {
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = b
    @Unique @Borrowed override fun <!VIPER_TEXT!>bump<!>() { b++ }
}

fun <!VIPER_TEXT!>staticBinding<!>(x: @Unique @Borrowed BaseMeter) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x.level() == x.a<!>)
}

fun <!VIPER_TEXT!>meterClient<!>(m: @Unique @Borrowed Meter) {
    preconditions { m.level() < 2147483647 }
    postconditions<Unit> { m.level() == old(m.level()) + 1 }
    val before = m.level()
    m.bump()
    verify(m.level() == before + 1)
}

fun <!VIPER_TEXT!>derivedThroughInterface<!>(d: @Unique @Borrowed DerivedMeter) {
    preconditions { d.level() < 2147483647 }
    meterClient(d)
}

fun <!VIPER_TEXT!>goodDerivedThroughBase<!>(d: @Unique GoodDerivedMeter) {
    preconditions { d.level() < 2147483647 }
    val base: @Unique BaseMeter = d
    meterClient(base)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>base.level() == base.a<!>)
}

fun <!VIPER_TEXT!>baseRoundTrip<!>(base: @Unique @Borrowed BaseMeter) {
    preconditions { base.level() < 2147483647 }
    val before = base.level()
    meterClient(base)
    verify(base.level() == before + 1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>base.level() == base.a<!>)
}

open class FinalObserver(var a: Int) : Meter {
    @Pure @Unique final override fun <!VIPER_TEXT!>level<!>(): Int = a
    @Unique @Borrowed override fun <!VIPER_TEXT!>bump<!>() { a++ }
}

interface DefaultMeter {
    @SpecOf("bump") fun bumpSpec() { postconditions<Unit> { true } }
    fun <!VIPER_TEXT!>bump<!>() {}
}
class ExplicitDefaultOverride : DefaultMeter {
    override fun <!VIPER_TEXT!>bump<!>() {}
}

interface PropertyMeter {
    @Pure val size: Int
}
open class OpenSize : PropertyMeter {
    @Pure override val size: Int <!VIPER_TEXT!>get<!>() = 3
}
class DerivedSize : OpenSize() {
    @Pure override val size: Int <!VIPER_TEXT!>get<!>() = 4
}
fun <!VIPER_TEXT!>propertyBinding<!>(s: @Unique @Borrowed OpenSize) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.size == 3<!>)
}
abstract class AbstractSize : PropertyMeter {
    @Pure abstract override val size: Int
}
class ConcreteSize : AbstractSize() {
    @Pure override val size: Int <!VIPER_TEXT!>get<!>() = 4
}
fun <!VIPER_TEXT!>abstractProperty<!>(s: @Unique @Borrowed AbstractSize): Int = s.size
fun <!VIPER_TEXT!>concreteProperty<!>(s: @Unique @Borrowed ConcreteSize) {
    verify(s.size == 4)
}

open <!VIPER_TEXT!>class ObserverMiddle<!>(a: Int, b: Int) : BaseMeter(a, b)
<!VIPER_TEXT!>class GoodObserverLeaf<!>(a: Int, b: Int) : ObserverMiddle(a, b)
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class BadObserverLeaf<!>(a: Int, b: Int) : ObserverMiddle(a, b) {
    @Pure @Unique override fun <!VIPER_TEXT!>level<!>(): Int = b
}<!>
