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
fun <!VIPER_TEXT!>subclassInitAssigns<!>() {
    val d: @Unique Derived = Derived(1)
    verify(<!VIPER_VERIFICATION_ERROR!>d.a == 1<!>)
}
