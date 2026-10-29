// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>secondReturnFails<!>(x: Int): Int {
    postconditions<Int> { it >= 0 }
    if (x > 0) return x
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return -1<!>
}

fun <!VIPER_TEXT!>endOfBodyFails<!>(x: Int) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    postconditions<Unit> { x > 0 }
    if (x > 1) return
}<!>

fun <!VIPER_TEXT!>allReturnsHold<!>(x: Int): Int {
    postconditions<Int> { it >= 0 }
    if (x > 0) return x
    return 0
}
