// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.postconditions

<!VIPER_TEXT!>class Ctx<!>(val name: Int)

@Pure
val Ctx.positive: Boolean <!VIPER_TEXT!>get<!>() = name > 0

fun <!VIPER_TEXT!>positiveOfName<!>(c: Ctx): Boolean {
    postconditions<Boolean> { it == (c.name > 0) }
    return c.positive
}

<!VIPER_TEXT!>class Outer<!>(val offset: Int) {
    val Ctx.isOrigin: Boolean <!VIPER_TEXT!>get<!>() = name == offset

    var Ctx.slot: Int
        <!VIPER_TEXT!>get<!>() = if (name > offset) name else offset
        <!VIPER_TEXT!>set<!>(v) {}

    @Pure
    val Ctx.atLeast: Boolean <!VIPER_TEXT!>get<!>() = name >= offset

    fun <!VIPER_TEXT!>readGetter<!>(c: Ctx): Boolean = c.isOrigin

    fun <!VIPER_TEXT!>writeSetter<!>(c: Ctx) {
        c.slot = 1
    }

    fun <!VIPER_TEXT!>atLeastUsesBoth<!>(c: Ctx): Boolean {
        postconditions<Boolean> { it == (c.name >= offset) }
        return c.atLeast
    }

    fun <!VIPER_TEXT!>atLeastIsNotPositive<!>(c: Ctx): Boolean {
        postconditions<Boolean> { it == (c.name >= 0) }
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return c.atLeast<!>
    }
}

fun <!VIPER_TEXT!>withDispatchReceiver<!>(outer: Outer, c: Ctx): Int = with(outer) { c.slot }
