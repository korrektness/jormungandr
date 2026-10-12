// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

interface Meter {
    @Pure @Unique fun level(): Int
    @SpecOf("bump") @Unique @Borrowed fun bumpSpec() {
        postconditions<Unit> { level() == old(level()) + 1 }
    }
    @Unique @Borrowed fun bump()
}

open class BaseMeter(var a: Int, var b: Int) : Meter {
    <!INVALID_OBSERVER!>@Pure @Unique override fun level(): Int = a<!>
    @Unique @Borrowed override fun bump() { a++ }
}

<!INVALID_OBSERVER!>class DerivedMeter : BaseMeter(0, 5) {
    @Pure @Unique override fun level(): Int = b
}<!>

open class TallyBase(var v: Int) {
    @Pure @Unique fun level(): Int = v
    @Unique @Borrowed fun bump() {}
}

<!INVALID_OBSERVER!>class InheritedMeter : TallyBase(0), Meter<!>

open class FinalObserver(var a: Int) : Meter {
    @Pure @Unique final override fun level(): Int = a
    @Unique @Borrowed override fun bump() { a++ }
}

interface DefaultMeter {
    @SpecOf("bump") fun bumpSpec() { postconditions<Unit> { true } }
    fun bump() {}
}
class ExplicitDefaultOverride : DefaultMeter {
    override fun bump() {}
}

interface PropertyMeter {
    <!INVALID_OBSERVER!>@Pure val size: Int<!>
}
class FixedSize : PropertyMeter {
    <!INVALID_OBSERVER!>@Pure override val size: Int get() = 3<!>
}
