// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Node<!>(val value: Int, var next: @Unique Node?)

@Pure
fun <!VIPER_TEXT!>isSorted<!>(n: @Unique Node?): Boolean {
    preconditions { decreases(n) }
    if (n == null) {
        return true
    }
    val m = n.next
    return if (m == null) {
        true
    } else {
        n.value <= m.value && isSorted(m)
    }
}

fun <!VIPER_TEXT!>keepSorted<!>(n: @Unique Node?): @Unique Node? {
    preconditions {
        isSorted(n)
    }
    postconditions<Node?> { r -> isSorted(r) }
    return n
}

fun <!VIPER_TEXT!>stillSorted<!>(n: @Unique @Borrowed Node) {
    preconditions {
        isSorted(n)
    }
    postconditions<Unit> {
        isSorted(n)
    }
}

<!VIPER_TEXT!>class Cell<!>(var content: Int)

<!VIPER_TEXT!>class Holder<!>(var cell: @Unique Cell)

@Pure
fun <!VIPER_TEXT!>content<!>(h: @Unique Holder): Int = h.cell.content

open <!VIPER_TEXT!>class Base<!>(var x: Int)

<!VIPER_TEXT!>class Derived<!>(x: Int, var y: Int) : Base(x)

@Pure
fun <!VIPER_TEXT!>sum<!>(d: @Unique Derived): Int {
    preconditions {
        d.x.toLong() + d.y.toLong() >= -2147483648L
        d.x.toLong() + d.y.toLong() <= 2147483647L
    }
    return d.x + d.y
}
