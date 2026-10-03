import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>longThenFinally<!>(x: Int): Int {
    <!UNSUPPORTED_FEATURE!>val s = <!UNSUPPORTED_FEATURE!>1L<!><!>
    <!UNSUPPORTED_FEATURE!>try {
        verify(x == x)
    } finally {
        verify(true)
    }<!>
    return x
}
