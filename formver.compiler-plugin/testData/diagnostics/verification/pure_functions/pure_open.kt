import org.jetbrains.kotlin.formver.plugin.*

open class Shape {
    <!UNSUPPORTED_FEATURE!>@Pure
    open fun sides(): Int = 0<!>
}

class Square : Shape() {
    <!INVALID_OBSERVER, UNSUPPORTED_FEATURE!>@Pure
    override fun sides(): Int = 4<!>
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>count<!>(s: Shape): Int = <!UNSUPPORTED_FEATURE!>s.sides()<!>
