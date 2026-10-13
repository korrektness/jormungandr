// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>normalizeLabel<!>(label: String): Int {
    val length = label.let { it.length }
    verify(length == label.length)
    val same = label.also { verify(it.length >= 0) }
    verify(same == label)
    return length
}

fun <!VIPER_TEXT!>receiverScopes<!>(label: String): Int {
    val same = label.apply { verify(length >= 0) }
    verify(same == label)
    val length = label.run { length }
    verify(length == label.length)
    val otherLength = with(label) { length }
    verify(otherLength == label.length)
    return run { otherLength }
}

fun <!VIPER_TEXT!>evaluatedReceiver<!>(): Int {
    var input = 7
    val saved = input.also { input = 9 }
    verify(saved == 7)
    verify(input == 9)
    return saved
}

fun <!VIPER_TEXT!>scopeAssertionFails<!>() {
    7.let { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>it == 8<!>) }
}

fun <!VIPER_TEXT!>scopeLocalReturn<!>(input: Int): Int {
    val value = input.let { if (it < 0) return@let 0; it }
    verify(value >= 0)
    return value
}
