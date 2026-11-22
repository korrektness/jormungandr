// FULL_JDK
// FILE: lib.kt
import org.jetbrains.kotlin.formver.plugin.*

<!NOTHING_TO_INLINE!>inline<!> fun <!VERIFICATION_SKIPPED!>bump<!>(x: Int) {
    var y = x
    verify(<!PURITY_VIOLATION!>++y > 0<!>)
}

// FILE: main.kt
import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>callsBump<!>() {
    val a = 1
    <!PURITY_VIOLATION!>bump(a)<!>
}

fun <!VIPER_TEXT!>independent<!>(): Int {
    verify(1 == 1)
    return 1
}
