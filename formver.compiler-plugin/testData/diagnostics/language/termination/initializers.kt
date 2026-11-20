// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>spinning<!>(x: Int): Int {
    while (x >= 0) {
    }
    return x
}

// Calling a function of this file may first run this initializer.
val started: Int = spinning(0)

<!TERMINATION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>checkedHere<!>(n: Int): Int {
    preconditions { decreases() }
    return n
}<!>
