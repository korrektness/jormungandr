// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class A {
    var x: @Unique Any = Any()
}

class B {
    var y: @Unique A = A()
}

fun consume(a: @Unique Any) {}

fun borrow(a: @Borrowed Any) {}

fun mayThrow() {}

fun `consume in try only`(a: @Unique A) {
    try {
        consume(a)
    } catch (_: Throwable) {}

    consume(<!INVALID_MOVED_ACCESS!>a<!>)
}

fun `consume in try and catch`(a: @Unique A) {
    try {
        consume(a)
    } catch (_: Throwable) {
        consume(<!INVALID_MOVED_ACCESS!>a<!>)
    }

    consume(a)
}

fun `try only side-effects`(a: @Unique A) {
    try {
        mayThrow()
        borrow(a)
    } catch (_: Throwable) {}

    consume(a)
}

fun `consume in finally`(a: @Unique A) {
    try {
        mayThrow()
    } finally {
        consume(a)
    }

    consume(<!INVALID_MOVED_ACCESS!>a<!>)
}

fun `consume in try then finally borrow`(a: @Unique A) {
    try {
        consume(a)
    } finally {
        borrow(<!INVALID_MOVED_ACCESS!>a<!>)
    }

    consume(a)
}

fun `try expression as initializer`(a: @Unique A): @Unique A {
    val r: @Unique A = try { a } catch (_: Throwable) { A() }
    consume(<!INVALID_MOVED_ACCESS!>a<!>)
    return r
}

fun `declarations in finally preserve the source and target paths on each exit`(
    early: Boolean,
    a: @Unique A,
    moved: @Unique A,
    inspected: @Unique Any,
) {
    try {
        if (early) {
            consume(a.x)
            return
        }
    } finally {
        val target: @Unique Any = moved.x
        consume(target)
        borrow(<!INVALID_MOVED_ACCESS!>moved.x<!>)

        when (inspected) {
            is A -> borrow(inspected)
        }
        consume(inspected)

        var uninitialized: @Unique Any
        uninitialized = Any()
        consume(uninitialized)
    }

    consume(a.x)
}

fun `consume subproperty in try`(b: @Unique B) {
    try {
        consume(b.y)
    } catch (_: Throwable) {}

    consume(<!ESCAPE_UNIQUENESS_INCONSISTENCY!>b<!>)
}

fun `consume in alternative catches`(a: @Unique A) {
    try {
        mayThrow()
    } catch (_: IllegalStateException) {
        consume(a)
    } catch (_: Throwable) {
        consume(a)
    }

    consume(<!INVALID_MOVED_ACCESS!>a<!>)
}

fun `try-catch consume unique subproperty after moving subpath`(a: @Unique A) {
    val y = a.x

    try {
        consume(y)
    } catch (e: Exception) {
    } finally {
        a.x = Any()
    }
}

fun `consume unique subproperty in try-catch`(a: @Borrowed @Unique A) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    try {
        consume(a.x)
    } catch (e: Exception) {
    } finally {
    }
}<!>

fun `consume unique subproperty in try-catch then re-initialize it in finally`(a: @Borrowed @Unique A) {
    try {
        consume(a.x)
    } catch (e: Exception) {
    } finally {
        a.x = Any()
    }
}
