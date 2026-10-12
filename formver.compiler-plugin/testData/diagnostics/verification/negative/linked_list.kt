// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.verify

class Link(var data: Int, val next: @Unique Link?)

fun <!VIPER_TEXT!>getVal<!>(l: @Unique Link): Int? {
    return l.next?.data
}

// these expressions should also all verify, but currently do not due to lack of uniqueness information
fun <!VIPER_TEXT!>test<!>() {
    val l = Link(5, Link(3, null))
    val expr1 = l.data == 5
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>expr1<!>)
    val expr2 = l.next?.data == 3
    verify(expr2)
}
