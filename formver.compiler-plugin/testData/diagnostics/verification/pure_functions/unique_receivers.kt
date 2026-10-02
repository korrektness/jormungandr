// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(val value: Int, var next: @Unique Node?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>isSorted<!>(n: @Unique Node?): Boolean {
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

@AlwaysVerify
fun <!VIPER_TEXT!>keepSorted<!>(n: @Unique Node?): @Unique Node? {
    preconditions {
        isSorted(n)
    }
    postconditions<Node?> { r -> isSorted(r) }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>stillSorted<!>(n: @Unique @Borrowed Node) {
    preconditions {
        isSorted(n)
    }
    postconditions<Unit> {
        isSorted(n)
    }
}

class Cell(var content: Int)

class Holder(var cell: @Unique Cell)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>content<!>(h: @Unique Holder): Int = h.cell.content

open class Base(var x: Int)

class Derived(x: Int, var y: Int) : Base(x)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>sum<!>(d: @Unique Derived): Int = d.x + d.y
