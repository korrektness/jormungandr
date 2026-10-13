// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Construction calls `compute`, which is final but calls the overridable `seed`.
<!INVALID_LAW!>open class Base {
    var v: Int = 1
    init { v = compute() }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>compute<!>(): Int {
        postconditions<Int> { r -> r > 0 }
        return seed()
    }

    @Unique @Borrowed
    open fun <!VIPER_TEXT!>seed<!>(): Int {
        postconditions<Int> { r -> r > 0 }
        return 1
    }

    @Law @Pure @Unique fun positive(): Boolean = v > 0
}<!>

<!INVALID_LAW!>class SubVar : Base() {
    var w: Int = 5

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>seed<!>(): Int = w

    @Law @Pure @Unique fun wPositive(): Boolean = w > 0
}<!>

<!INVALID_LAW!>class SubVal : Base() {
    val w: Int = 5
    var q: Int = 0

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>seed<!>(): Int {
        val unused = q
        return w
    }

    @Law @Pure @Unique fun wPositive(): Boolean = w > 0 && q == 0
}<!>

fun <!VIPER_TEXT!>subVarClient<!>() {
    val s: @Unique SubVar = SubVar()
    verify(s.v > 0)
    refute(false)
}

fun <!VIPER_TEXT!>subValClient<!>() {
    val s: @Unique SubVal = SubVal()
    verify(s.v > 0)
    refute(false)
}

// A subclass that overrides nothing inherits the rejected construction; its clients keep the class laws.
<!INVALID_LAW!>class Benign : Base() {
    var w: Int = 5
}<!>

fun <!VIPER_TEXT!>benignClient<!>() {
    val s: @Unique Benign = Benign()
    verify(s.v > 0)
}

// A construction whose calls on `this` are fixed all the way down verifies, and so does a subclass's.
open <!VIPER_TEXT!>class FixedBase<!> {
    var v: Int = 1
    init { v = compute() }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>compute<!>(): Int {
        postconditions<Int> { r -> r > 0 }
        return seed()
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>seed<!>(): Int {
        postconditions<Int> { r -> r > 0 }
        return 1
    }

    @Law @Pure @Unique fun positive(): Boolean = v > 0
}

<!VIPER_TEXT!>class FixedSub<!> : FixedBase() {
    var w: Int = 5
}

fun <!VIPER_TEXT!>fixedSubClient<!>() {
    val s: @Unique FixedSub = FixedSub()
    verify(s.v > 0)
    refute(false)
}

// A callee that receives `this` of an open class may dispatch to an override.
fun <!VIPER_TEXT!>seedOf<!>(b: @Unique @Borrowed PassedBase): Int {
    postconditions<Int> { r -> r > 0 }
    return b.seed()
}

<!INVALID_LAW!>open class PassedBase {
    var v: Int = 1
    init { v = seedOf(this) }

    @Unique @Borrowed
    open fun <!VIPER_TEXT!>seed<!>(): Int {
        postconditions<Int> { r -> r > 0 }
        return 1
    }

    @Law @Pure @Unique fun positive(): Boolean = v > 0
}<!>

fun PassedExtension.<!VIPER_TEXT!>extensionSeed<!>(): Int {
    postconditions<Int> { r -> r > 0 }
    return 1
}

open <!VERIFICATION_SKIPPED!>class PassedExtension<!> {
    var v: Int = 1
    init { v = extensionSeed() }
}
