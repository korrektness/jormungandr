import org.jetbrains.kotlin.formver.plugin.*

fun localClassProperty(): Int {
    <!UNSUPPORTED_FEATURE!>class L(<!INTERNAL_ERROR!>val x: Int<!>)<!>
    return L(1).x
}
