// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Checked<!>(var a: Int, val b: Int) {
    var sum = a.toLong() + b.toLong()

    init {
        verify(a == a)
    }
}

<!VIPER_TEXT!>class Reset<!>(var a: Int, val b: Int) {
    init {
        a = 0
    }
}

<!VIPER_TEXT!>class Lending<!>(var a: Int, val b: Int) {
    init {
        clear()
    }

    @Borrowed
    fun <!VIPER_TEXT!>clear<!>() {
        a = 0
    }
}

<!VIPER_TEXT!>class Resetting<!>(var a: Int, val b: Int) {
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

<!VIPER_TEXT!>class Node<!>(var value: Int)

<!VIPER_TEXT!>class Holder<!>(val child: @Unique Node) {
    init {
        clearChild()
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>clearChild<!>() {
        child.value = 0
    }
}

<!VIPER_TEXT!>class DirectHolder<!>(val child: @Unique Node) {
    init {
        child.value = 0
    }
}

<!VIPER_TEXT!>class QuietHolder<!>(val child: @Unique Node)

open <!VIPER_TEXT!>class Base<!>(var a: Int)

<!VIPER_TEXT!>class Derived<!>(a: Int) : Base(a) {
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
<!VERIFICATION_SKIPPED!>class ForwardLoanSource<!>(sink: @Unique @Borrowed ForwardLoanSink) {
    init { sink.accept(this) }
    val age = 1
}
fun <!VIPER_TEXT!>forwardLoanClient<!>() {
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
