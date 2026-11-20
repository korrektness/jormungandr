// NEVER_VALIDATE

import org.jetbrains.kotlin.formver.plugin.*

@Pure
fun <!VERIFICATION_SKIPPED!>pureWithClause<!>(x: Int): Int {
    throwsOnlyIf { <!UNSUPPORTED_FEATURE!>x < 0<!> }
    return x
}

<!UNSUPPORTED_FEATURE!>class Built(val x: Int) {
    constructor(x: Int, y: Int) : this(x + y) {
        throwsOnlyIf { y < 0 }
    }
}<!>
