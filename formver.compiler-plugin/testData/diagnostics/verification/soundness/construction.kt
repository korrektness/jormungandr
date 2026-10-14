// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Facts assumed when an object is created (docs/soundness.md, L2, L3 and M3).

open <!VIPER_TEXT!>class Animal<!>
<!VIPER_TEXT!>class Dog<!> : Animal()

// An allocation fixes the exact runtime type, not a subtype.
fun <!VIPER_TEXT!>allocationExactType<!>() {
    val a: Animal = Animal()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a is Dog<!>)
}

open <!VIPER_TEXT!>class GetterBase<!> {
    open val x: Int = 1
}

<!VIPER_TEXT!>class GetterSub<!> : GetterBase() {
    override val x: Int = 2
}

// Construction fixes each getter once per receiver, so an overridden `val` gives no contradiction.
fun <!VIPER_TEXT!>overriddenValInit<!>() {
    val s = GetterSub()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class Impossible<!>(var n: Int) {
    @Law @Pure @Unique fun never(): Boolean = n > n
}<!>

// The constructor header promises the law, and only the class's own verification refutes it.
fun <!VIPER_TEXT!>constructImpossible<!>() {
    val s = Impossible(0)
    verify(false)
}
