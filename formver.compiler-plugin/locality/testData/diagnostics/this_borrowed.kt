// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

fun store(x: Any) {}

fun borrow(x: @Borrowed Any) {}

class A(val x: Any) {
    @Borrowed
    fun `return borrowed this`(): A {
        return <!LOCALITY_MISMATCH!>this<!>
    }

    @Borrowed
    fun `store borrowed this`() {
        store(<!LOCALITY_MISMATCH!>this<!>)
    }

    @Borrowed
    fun `lend borrowed this`() {
        borrow(this)
    }

    @Borrowed
    fun `return field of borrowed this`(): Any {
        return x
    }

    @Borrowed
    fun `call borrowing member`() {
        peek()
        this.peek()
    }

    @Borrowed
    fun `call consuming member`() {
        <!LOCALITY_MISMATCH!>consume()<!>
    }

    @Borrowed
    fun `call unannotated member`() {
        plain()
    }

    fun `return shared this`(): A {
        return this
    }

    @Borrowed
    fun peek() {}

    @Unique
    fun consume() {}

    fun plain() {}
}

<!INVALID_LOCALITY_TYPE_TARGET!>@Borrowed<!>
fun topLevel() {}
