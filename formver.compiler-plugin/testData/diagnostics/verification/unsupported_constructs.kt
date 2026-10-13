// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>longThenFinally<!>(x: Int): Int {
    val s = <!UNSUPPORTED_FEATURE!>1.0<!>
    try {
        verify(x == x)
    } finally {
        verify(true)
    }
    return x
}
