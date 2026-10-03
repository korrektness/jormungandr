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

@AlwaysVerify
fun <!VIPER_TEXT!>keepsParameters<!>() {
    val c: @Unique Checked = Checked(1, 2)
    verify(c.a == 1, c.b == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initAssigns<!>() {
    val r: @Unique Reset = Reset(1, 2)
    verify(r.b == 2)
    verify(<!VIPER_VERIFICATION_ERROR!>r.a == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initLendsThis<!>() {
    val l: @Unique Lending = Lending(1, 2)
    verify(l.b == 2)
    verify(<!VIPER_VERIFICATION_ERROR!>l.a == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initOwnsThis<!>() {
    val r: @Unique Resetting = Resetting(1, 2)
    verify(r.b == 2)
    verify(<!VIPER_VERIFICATION_ERROR!>r.a == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>subclassInitAssigns<!>() {
    val d: @Unique Derived = Derived(1)
    verify(<!VIPER_VERIFICATION_ERROR!>d.a == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>memberChangesChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique Holder = Holder(n)
    verify(<!VIPER_VERIFICATION_ERROR!>h.child.value == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>initChangesChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique DirectHolder = DirectHolder(n)
    verify(<!VIPER_VERIFICATION_ERROR!>h.child.value == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>keepsChild<!>(n: @Unique Node) {
    n.value = 1
    val h: @Unique QuietHolder = QuietHolder(n)
    verify(h.child.value == 1)
}
