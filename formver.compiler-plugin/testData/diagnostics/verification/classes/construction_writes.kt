// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Checked(var a: Int, val b: Int) {
    var sum = a + b

    init {
        verify(a == a)
    }
}

class Reset(var a: Int, val b: Int) {
    init {
        a = 0
    }
}

class Lending(var a: Int, val b: Int) {
    init {
        clear()
    }

    @Borrowed
    fun <!VIPER_TEXT!>clear<!>() {
        a = 0
    }
}

class Resetting(var a: Int, val b: Int) {
    var c: Int = 1

    init {
        reset()
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>reset<!>() {
        postconditions<Unit> { a == 0 && c == 0 }
        a = 0
        c = 0
    }
}

class Node(var value: Int)

class Holder(val child: @Unique Node) {
    init {
        clearChild()
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>clearChild<!>() {
        child.value = 0
    }
}

class DirectHolder(val child: @Unique Node) {
    init {
        child.value = 0
    }
}

class QuietHolder(val child: @Unique Node)

open class Base(var a: Int)

class Derived(a: Int) : Base(a) {
    init {
        this.a = 0
    }
}

<!VIPER_TEXT!>class ForwardLoanSink<!>(var n: Int) {
    init { require(n >= 1) }
    @Law @Pure fun invariant(): Boolean = n >= 1
    @Unique @Borrowed fun <!VIPER_TEXT!>accept<!>(source: @Borrowed ForwardLoanSource) {
        preconditions { source.age >= 1 }
        n = source.age
    }
}
class ForwardLoanSource<!UNSUPPORTED_FEATURE!>(sink: @Unique @Borrowed ForwardLoanSink)<!> {
    init { sink.accept(this) }
    val age = 1
}
fun <!VERIFICATION_SKIPPED!>forwardLoanClient<!>() {
    val sink: @Unique ForwardLoanSink = ForwardLoanSink(1)
    val source: @Unique ForwardLoanSource = ForwardLoanSource(sink)
    verify(sink.n >= 1)
}

fun <!VIPER_TEXT!>keepsParameters<!>() {
    val c: @Unique Checked = Checked(1, 2)
    verify(c.a == 1, c.b == 2)
}

fun <!VIPER_TEXT!>initAssigns<!>() {
    val r: @Unique Reset = Reset(1, 2)
    verify(r.b == 2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r.a == 1<!>)
}

fun <!VIPER_TEXT!>initLendsThis<!>() {
    val l: @Unique Lending = Lending(1, 2)
    verify(l.b == 2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>l.a == 1<!>)
}

fun <!VIPER_TEXT!>initOwnsThis<!>() {
    val r: @Unique Resetting = Resetting(1, 2)
    verify(r.b == 2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r.a == 1<!>)
}

fun <!VIPER_TEXT!>subclassInitAssigns<!>() {
    val d: @Unique Derived = Derived(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d.a == 1<!>)
}

fun <!VIPER_TEXT!>memberChangesChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique Holder = Holder(n)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>h.child.value == 1<!>)
}

fun <!VIPER_TEXT!>initChangesChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique DirectHolder = DirectHolder(n)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>h.child.value == 1<!>)
}

fun <!VIPER_TEXT!>keepsChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique QuietHolder = QuietHolder(n)
    verify(h.child.value == 1)
}
