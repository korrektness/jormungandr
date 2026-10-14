// FULL_JDK
// ALWAYS_VALIDATE
// COUNTEREXAMPLES

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>integer<!>(x: Int) {
    preconditions { x == 7 }
    val local = x + 1
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>local != 8<!>)
}

fun <!VIPER_TEXT!>boolean<!>(x: Boolean) {
    preconditions { !x }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x<!>)
}

fun <!VIPER_TEXT!>character<!>(x: Char) {
    preconditions { x == '\n' }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != '\n'<!>)
}

fun <!VIPER_TEXT!>nullable<!>(x: Int?) {
    preconditions { x == null }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != null<!>)
}

enum class Choice { ONLY }

fun <!VIPER_TEXT!>enumLike<!>(x: Choice) {
    preconditions { x == Choice.ONLY }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != Choice.ONLY<!>)
}

<!VIPER_TEXT!>class CounterexampleBox<!>(var value: Int)

fun <!VIPER_TEXT!>fields<!>(box: @Unique @Borrowed CounterexampleBox) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions { box.value == 3 }
    postconditions<Unit> { box.value == old(box.value) }
    box.value = 4
}<!>

fun (@Unique @Borrowed CounterexampleBox).<!VIPER_TEXT!>receiver<!>() {
    preconditions { value == 3 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>value != 3<!>)
}
