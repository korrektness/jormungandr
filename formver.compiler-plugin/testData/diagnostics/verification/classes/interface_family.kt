// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Family {
    @Pure
    @Unique
    fun <!VIPER_TEXT!>observe<!>(): Int
}
interface LeftFamily : Family
interface RightFamily : Family
interface DiamondFamily : LeftFamily, RightFamily

class Representation(var value: Int) : DiamondFamily {
    @Pure
    @Unique
    @AlwaysVerify
    override fun <!VIPER_TEXT!>observe<!>(): Int = value
}

class OtherRepresentation(var other: Int) : Family {
    @Pure
    @Unique
    @AlwaysVerify
    override fun <!VIPER_TEXT!>observe<!>(): Int = other
}

@NeverConvert
fun consume(value: @Unique Family) {}

@NeverConvert
fun consumeSerializable(value: @Unique java.io.Serializable) {}

@NeverConvert
fun consumeAppendable(value: @Unique Appendable) {}

@AlwaysVerify
fun <!VIPER_TEXT!>arrayFamily<!>(value: @Unique IntArray) {
    refute(false)
    consumeSerializable(value)
}

@AlwaysVerify
fun <!VIPER_TEXT!>builderFamily<!>(value: @Unique StringBuilder) {
    refute(false)
    consumeAppendable(value)
}

@AlwaysVerify
fun <!VIPER_TEXT!>passOwned<!>(value: @Unique Representation) {
    refute(false)
    consume(value)
}

@AlwaysVerify
fun <!VIPER_TEXT!>passFresh<!>() {
    refute(false)
    consume(Representation(1))
}

@AlwaysVerify
fun <!VIPER_TEXT!>passConditional<!>(choose: Boolean) {
    refute(false)
    consume(if (choose) Representation(1) else OtherRepresentation(2))
}

@AlwaysVerify
fun <!VIPER_TEXT!>localFresh<!>() {
    val view: @Unique Family = Representation(3)
    refute(false)
    consume(view)
}

@AlwaysVerify
fun <!VIPER_TEXT!>localOwned<!>(value: @Unique Representation) {
    val view: @Unique Family = value
    refute(false)
    consume(view)
}

@AlwaysVerify
fun <!VIPER_TEXT!>assignOwned<!>(value: @Unique Representation, initial: @Unique Family) {
    var view: @Unique Family = initial
    view = value
    refute(false)
    consume(view)
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnOwned<!>(value: @Unique Representation): @Unique Family {
    refute(false)
    return value
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnFresh<!>(): @Unique Family {
    refute(false)
    return Representation(2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnConditional<!>(choose: Boolean): @Unique Family {
    refute(false)
    return if (choose) Representation(1) else OtherRepresentation(2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>diamondOwned<!>(value: @Unique Representation) {
    value.value = 8
    val view: @Unique DiamondFamily = value
    val ancestor: @Unique Family = view
    verify(ancestor.observe() == 8)
    refute(false)
    consume(ancestor)
}

@AlwaysVerify
fun <!VIPER_TEXT!>nullableOwned<!>(value: @Unique Representation?): @Unique Family? {
    refute(false)
    return value
}

@AlwaysVerify
fun <!VIPER_TEXT!>observeFamily<!>(value: @Unique @Borrowed Family): Int {
    postconditions<Int> { r -> r == value.observe() }
    refute(false)
    return value.observe()
}
