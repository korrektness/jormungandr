import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>templateThenFinally<!>(x: Int): Int {
    val s = "x is $<!UNSUPPORTED_FEATURE!>x<!>"
    <!UNSUPPORTED_FEATURE!>try {
        verify(x == x)
    } finally {
        verify(true)
    }<!>
    return x
}
