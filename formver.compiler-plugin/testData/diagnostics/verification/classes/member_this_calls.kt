// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open class Counter(var count: Int) {
    fun <!VIPER_TEXT!>plain<!>() {}

    @Borrowed
    fun <!VIPER_TEXT!>peek<!>() {}

    @Unique @Borrowed
    fun <!VIPER_TEXT!>bump<!>() {
        postconditions<Unit> { count == old(count) + 1 }
        count = count + 1
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>touch<!>() {
        count = count + 1
    }

    @Pure @Unique
    fun <!VIPER_TEXT!>value<!>(): Int = count

    @Unique
    fun <!VIPER_TEXT!>finish<!>() {}

    @AlwaysVerify @Unique
    fun <!VIPER_TEXT!>consumeByPlain<!>() {
        count = 1
        plain()
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>keepAfterPeek<!>() {
        peek()
        count = 2
        verify(count == 2)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>staleAfterPeek<!>() {
        count = 2
        peek()
        verify(<!VIPER_VERIFICATION_ERROR!>count == 2<!>)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>restatedByBump<!>() {
        count = 2
        bump()
        verify(count == 3)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>staleAfterTouch<!>() {
        count = 2
        touch()
        verify(<!VIPER_VERIFICATION_ERROR!>count == 2<!>)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>keepAfterValue<!>() {
        count = 2
        val v = value()
        verify(v == 2, count == 2)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>wrongValue<!>() {
        count = 2
        verify(<!VIPER_VERIFICATION_ERROR!>value() == 3<!>)
    }

    @AlwaysVerify @Unique
    fun <!VIPER_TEXT!>consumeByFinish<!>() {
        count = 1
        finish()
    }
}

class Tally(count: Int, var total: Int) : Counter(count) {
    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>bumpInBase<!>() {
        count = 2
        bump()
        verify(count == 3)
    }

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>subclassFieldsLost<!>() {
        total = 5
        bump()
        verify(<!VIPER_VERIFICATION_ERROR!>total == 5<!>)
    }
}

class Built(var a: Int, val tag: Int) {
    var b: Int = 0

    init {
        reset()
        peek()
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>reset<!>() {
        postconditions<Unit> { a == 0 && b == 0 }
        a = 0
        b = 0
    }

    @Borrowed
    fun <!VIPER_TEXT!>peek<!>() {}
}

@AlwaysVerify
fun <!VIPER_TEXT!>builtKeepsTag<!>() {
    val x: @Unique Built = Built(1, 2)
    verify(x.tag == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>builtKeepsA<!>() {
    val x: @Unique Built = Built(1, 2)
    verify(<!VIPER_VERIFICATION_ERROR!>x.a == 1<!>)
}
