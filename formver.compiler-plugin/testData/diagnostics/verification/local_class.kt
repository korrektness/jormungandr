import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>localClassProperty<!>(): Int {
    <!UNSUPPORTED_FEATURE!>class L(<!UNSUPPORTED_FEATURE!>val x: Int<!>)<!>
    return L(1).x
}
