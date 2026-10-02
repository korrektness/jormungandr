// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Node(var value: Int)

fun <!VIPER_TEXT!>sharedNode<!>(): Node = Node(0)

fun <!VIPER_TEXT!>writeThroughConstructedLocal<!>() {
    val n = Node(0)
    <!UNTRACKED_WRITE!>n.value = 1<!>
}

fun <!VIPER_TEXT!>storeThroughConstructedArray<!>() {
    val a = IntArray(3)
    <!UNTRACKED_WRITE!>a[0] = 1<!>
    <!UNTRACKED_WRITE!>a[1] += 1<!>
    <!UNTRACKED_WRITE!>a.set(2, 1)<!>
}

fun <!VIPER_TEXT!>writeThroughUniqueLocal<!>() {
    val n: @Unique Node = Node(0)
    n.value = 1
    val a: @Unique IntArray = IntArray(3)
    a[0] = 1
}

fun <!VIPER_TEXT!>writeThroughCallResult<!>() {
    val n = sharedNode()
    n.value = 1
}

fun <!VIPER_TEXT!>writeThroughSharedParameter<!>(n: Node, a: IntArray) {
    n.value = 1
    a[0] = 1
}
