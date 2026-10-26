// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed

fun borrow(x: @Borrowed Any) {}

fun outer(x: @Borrowed Any) {
    fun named() { borrow(x) }

    val direct: () -> Unit = <!LOCALITY_MISMATCH!>{ named() }<!>
    val reference: () -> Unit = <!LOCALITY_MISMATCH!>::named<!>
    val borrowed: @Borrowed () -> Unit = { named() }
    borrowed()
}

fun `transitive local function calls retain captures`(x: @Borrowed Any) {
    fun first() { borrow(x) }
    fun second() { first() }

    val escaped: () -> Unit = <!LOCALITY_MISMATCH!>{ second() }<!>
}

fun `calling a local function declared inside a lambda does not capture its locals`() {
    val callback: () -> Unit = {
        val x: @Borrowed Any = Any()
        fun named() { borrow(x) }
        named()
    }
}

fun `recursive local function retains captures`(x: @Borrowed Any) {
    fun recursive(again: Boolean) {
        if (again) recursive(false) else borrow(x)
    }

    val escaped: () -> Unit = <!LOCALITY_MISMATCH!>{ recursive(true) }<!>
}

fun `recursive local function with no captures may escape`() {
    fun recursive(again: Boolean) {
        if (again) recursive(false)
    }

    val callback: () -> Unit = { recursive(true) }
}
