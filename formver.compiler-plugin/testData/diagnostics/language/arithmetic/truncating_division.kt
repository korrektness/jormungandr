// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>negativeDividend<!>(a: Int) {
    preconditions { a == -3 }
    verify(a / 2 == -1, a % 2 == -1, a / -2 == 1, a % -2 == -1)
}

fun <!VIPER_TEXT!>negativeDivisor<!>(a: Int) {
    preconditions { a == 3 }
    verify(a / -2 == -1, a % -2 == 1, a / 2 == 1, a % 2 == 1)
}

fun <!VIPER_TEXT!>zeroDividend<!>(a: Int, b: Int) {
    preconditions { a == 0; b != 0 }
    verify(a / b == 0, a % b == 0)
}

fun <!VIPER_TEXT!>minimumRemainder<!>(a: Int) {
    preconditions { a == -2147483648 }
    verify(a % -1 == 0)
}

fun <!VIPER_TEXT!>rejectsEuclideanQuotient<!>(a: Int) {
    preconditions { a == -3 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a / 2 == -2<!>)
}

fun <!VIPER_TEXT!>rejectsEuclideanRemainder<!>(a: Int) {
    preconditions { a == -3 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a % 2 == 1<!>)
}

@Pure
fun <!VIPER_TEXT!>quotient<!>(a: Int, b: Int): Int {
    preconditions { b != 0; a != -2147483648 || b != -1 }
    return a / b
}

@Pure
fun <!VIPER_TEXT!>remainder<!>(a: Int, b: Int): Int {
    preconditions { b != 0 }
    return a % b
}

fun <!VIPER_TEXT!>pureSignedArithmetic<!>(a: Int) {
    preconditions { a == -3 }
    verify(quotient(a, 2) == -1, remainder(a, 2) == -1)
}
