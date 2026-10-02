// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Node(var value: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>borrow<!>(n: @Borrowed Node) {}

@AlwaysVerify
fun <!VIPER_TEXT!>consume<!>(n: @Unique Node) {}

fun <!VERIFICATION_SKIPPED!>tryWithUniqueParameter<!>(n: @Unique Node) {
    <!UNSUPPORTED_OWNERSHIP!>try {
        n.value = 1
    } catch (e: Exception) {
    }<!>
}

fun <!VERIFICATION_SKIPPED!>tryWithUniqueLocal<!>() {
    <!UNSUPPORTED_OWNERSHIP!>try {
        val n: @Unique Node = Node(0)
        consume(n)
    } catch (e: Exception) {
    }<!>
}

@AlwaysVerify
fun <!VIPER_TEXT!>tryPassingConstructorResults<!>() {
    try {
        consume(Node(1))
        borrow(Node(2))
    } catch (e: Exception) {
    }
}
