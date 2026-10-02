// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Inner(var x: Int)

class Outer(var inner: @Unique Inner, var y: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>freshNested<!>() {
    val o: @Unique Outer = Outer(Inner(0), 1)
    verify(o.inner.x == 0 && o.y == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>storedLocal<!>() {
    val i: @Unique Inner = Inner(3)
    i.x = 4
    val o: @Unique Outer = Outer(i, 1)
    verify(o.inner.x == 4)
}

open class Sup(var s: @Unique Inner)

class Sub(s: @Unique Inner, var t: Int) : Sup(s)

@AlwaysVerify
fun <!VIPER_TEXT!>storedBySuperclass<!>(i: @Unique Inner) {
    i.x = 7
    val sub: @Unique Sub = Sub(i, 2)
    verify(sub.s.x == 7 && sub.t == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>storedBySuperclassProbeEnd<!>(i: @Unique Inner) {
    val sub: @Unique Sub = Sub(i, 2)
    verify(sub.s.x == sub.s.x)
    verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
}

class Holder(var y: Int) {
    var inner: @Unique Inner = Inner(0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initializedInBody<!>() {
    val h: @Unique Holder = Holder(1)
    h.inner.x = 2
    verify(h.y == 1 && h.inner.x == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initializedInBodyUnconstrained<!>() {
    val h: @Unique Holder = Holder(1)
    verify(<!VIPER_VERIFICATION_ERROR!>h.inner.x == 0<!>)
}

class Node(val value: Int, var next: @Unique Node?)

@AlwaysVerify
fun <!VIPER_TEXT!>twoNodes<!>() {
    val l: @Unique Node = Node(1, Node(2, null))
    val n = l.next
    verify(l.value == 1 && n != null)
    if (n != null) {
        verify(n.value == 2 && n.next == null)
    }
}

class Buf(val data: @Unique IntArray, var len: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>storedInVal<!>(a: @Unique IntArray) {
    preconditions { a.size == 3 }
    a[0] = 5
    val b: @Unique Buf = Buf(a, 1)
    verify(b.data.size == 3 && b.data[0] == 5 && b.len == 1)
}

@Manual
class Tree(var left: @Unique Tree?, var data: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>manualStored<!>(l: @Unique Tree) {
    unfold(UniquePred(l))
    l.data = 5
    fold(UniquePred(l))
    val t: @Unique Tree = Tree(l, 1)
    unfold(UniquePred(t))
    verify(t.data == 1 && t.left != null)
    val left = t.left
    if (left != null) {
        unfold(UniquePred(left))
        verify(left.data == 5)
    }
}
