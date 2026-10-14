// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Sized {
    @Pure val size: Int
    @Law @Pure fun nonnegative(): Boolean = size >= 0
}
<!VIPER_TEXT!>class FixedSize<!> : Sized {
    @Pure override val size: Int <!VIPER_TEXT!>get<!>() = 3
}
fun <!VIPER_TEXT!>propertyLaw<!>(s: @Unique @Borrowed Sized) { verify(s.size >= 0) }
fun <!VIPER_TEXT!>fixedProperty<!>() {
    val s: @Unique FixedSize = FixedSize()
    val view: @Unique Sized = s
    verify(view.size == 3)
}

interface Growing : Sized {
    @SpecOf("grow") @Unique @Borrowed fun growSpec() {
        preconditions { size <= 2147483645 }
        postconditions<Unit> { size == old(size) + 1 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>grow<!>()
}
<!VIPER_TEXT!>class MutableSize<!>(var n: Int) : Growing {
    init { require(n >= 0) }
    @Law @Pure fun invariant(): Boolean = size >= 0
    @get:Unique @Pure override val size: Int <!VIPER_TEXT!>get<!>() = n
    @Unique @Borrowed override fun <!VIPER_TEXT!>grow<!>() { n++ }
}
fun <!VIPER_TEXT!>growClient<!>(s: @Unique @Borrowed Growing) {
    preconditions { s.size <= 2147483645 }
    postconditions<Unit> { s.size == old(s.size) + 1 }
    val before = s.size
    s.grow()
    verify(s.size == before + 1)
}
fun <!VIPER_TEXT!>borrowedProperty<!>(s: @Unique @Borrowed MutableSize) {
    preconditions { s.size <= 2147483645 }
    val before = s.size
    growClient(s)
    verify(s.size == before + 1)
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class BrokenSize<!> : Sized {
    @Pure override val size: Int <!VIPER_TEXT!>get<!>() = -1
}<!>
<!VIPER_TEXT!>class WrongGrow<!>(var n: Int) : Growing {
    init { require(n >= 0) }
    @Law @Pure fun invariant(): Boolean = n >= 0
    @get:Unique @Pure override val size: Int <!VIPER_TEXT!>get<!>() = n
    @Unique @Borrowed override fun <!VIPER_TEXT!>grow<!>() <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ n += 2 }<!>
}
fun <!VIPER_TEXT!>wrongObservation<!>(s: @Unique @Borrowed Sized) { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.size < 0<!>) }
