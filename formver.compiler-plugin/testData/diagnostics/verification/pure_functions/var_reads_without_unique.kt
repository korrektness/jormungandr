// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Box(var content: Int)

class Node(val value: Int, var next: @Unique Node?)

<!VIPER_VERIFICATION_ERROR!>@Pure
fun <!VERIFICATION_SKIPPED!>readShared<!>(b: Box): Int = <!UNSUPPORTED_OWNERSHIP!>b.content<!><!>

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>readUnique<!>(b: @Unique Box): Int = b.content

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>callsShared<!>(b: Box): Int {
    <!VIPER_VERIFICATION_ERROR!>preconditionShared(b)<!>
    return readShared(b)
}<!>

fun <!VERIFICATION_SKIPPED!>preconditionShared<!>(b: Box) {
    preconditions {
        <!UNSUPPORTED_OWNERSHIP!>b.content<!> > 0
    }
}

fun <!VERIFICATION_SKIPPED!>invariantShared<!>(b: Box) {
    var i = 0
    while (i < 1) {
        loopInvariants {
            <!UNSUPPORTED_OWNERSHIP!>b.content<!> >= 0
        }
        i++
    }
}

fun <!VERIFICATION_SKIPPED!>verifyShared<!>(b: Box) {
    verify(<!UNSUPPORTED_OWNERSHIP!>b.content<!> == <!UNSUPPORTED_OWNERSHIP!>b.content<!>)
}

fun <!VERIFICATION_SKIPPED!>postconditionConsumed<!>(n: @Unique Node) {
    postconditions<Unit> {
        <!UNSUPPORTED_OWNERSHIP!>n.next<!> == null
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>postconditionConsumedOld<!>(n: @Unique Node) {
    postconditions<Unit> {
        old(n.next) == old(n.next)
        n.value == old(n.value)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>postconditionBorrowed<!>(n: @Unique @Borrowed Node) {
    preconditions {
        n.next == null
    }
    postconditions<Unit> {
        n.next == null
    }
}
