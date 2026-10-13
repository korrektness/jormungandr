// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.verify

<!VIPER_TEXT!>class Node<!>(var data: Int, val left: @Unique Node?, val right: @Unique Node?)

fun <!VIPER_TEXT!>get_left_val<!>(n: @Unique Node): Int? {
    return n.left?.data
}

// these expressions should all verify - they currently do not due to lack of uniqueness information
fun <!VIPER_TEXT!>test<!>() {
    val n = Node(5, Node(4, null, null), Node(3, Node(2, null, null), Node(1, null, null)))
    val expr1 = n.data == 5
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>expr1<!>)
    val expr2 = n.left?.data == 4
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>expr2<!>)
}
