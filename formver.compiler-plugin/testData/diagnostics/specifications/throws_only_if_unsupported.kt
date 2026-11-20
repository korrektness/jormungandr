// NEVER_VALIDATE

import org.jetbrains.kotlin.formver.plugin.*

@Pure
fun <!VERIFICATION_SKIPPED!>pureWithClause<!>(x: Int): Int {
    throwsOnlyIf { <!UNSUPPORTED_FEATURE!>x < 0<!> }
    return x
}

fun <!VERIFICATION_SKIPPED!>callsPure<!>(x: Int): Int = <!UNSUPPORTED_FEATURE!>pureWithClause(x)<!>

fun <!VERIFICATION_SKIPPED!>callsPureTwice<!>(x: Int): Int {
    val first = <!UNSUPPORTED_FEATURE!>pureWithClause(x)<!>
    <!UNSUPPORTED_FEATURE!>return pureWithClause(first)<!>
}

<!UNSUPPORTED_FEATURE!>class Built(val x: Int) {
    constructor(x: Int, y: Int) : this(x + y) {
        throwsOnlyIf { y < 0 }
    }
}<!>
