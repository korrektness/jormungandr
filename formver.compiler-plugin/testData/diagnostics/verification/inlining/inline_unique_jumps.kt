// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

class Holder(var inner: @Unique Counter, var count: Int)

inline fun <!VIPER_TEXT!>borrowThen<!>(c: @Unique @Borrowed Counter, f: () -> Unit) {
    c.x = 1
    f()
}

inline fun <!VIPER_TEXT!>consumeThen<!>(c: @Unique Counter, f: () -> Unit) {
    c.x = 1
    f()
}

inline fun <!VIPER_TEXT!>borrowTwiceThen<!>(c: @Unique @Borrowed Counter, f: () -> Unit) {
    borrowThen(c) { f() }
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnThroughBorrowedPath<!>(h: @Unique @Borrowed Holder, b: Boolean) {
    borrowThen(h.inner) {
        if (b) return
    }
    verify(h.inner.x == 1)
}

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>wrongReturnThroughBorrowedPath<!>(h: @Unique @Borrowed Holder) {
    postconditions<Unit> { h.inner.x == 0 }
    borrowThen(h.inner) {
        return
    }
}<!>

@AlwaysVerify
fun <!VIPER_TEXT!>returnThroughBorrowedPathKeepsSibling<!>(h: @Unique Holder): Int {
    postconditions<Int> { r -> r == 2 }
    h.count = 2
    borrowThen(h.inner) {
        return h.count
    }
    return 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnThroughNestedBorrows<!>(h: @Unique @Borrowed Holder, b: Boolean) {
    borrowTwiceThen(h.inner) {
        if (b) return
    }
    verify(h.inner.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnThroughConsumedPath<!>(h: @Unique Holder, b: Boolean) {
    consumeThen(h.inner) {
        if (b) return
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>breakThroughBorrowedPath<!>(h: @Unique @Borrowed Holder, n: Int) {
    var i = 0
    while (i < n) {
        borrowThen(h.inner) {
            if (i == 3) break
        }
        i = i + 1
    }
    h.inner.x = 2
    verify(h.inner.x == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>continueThroughBorrowedPath<!>(h: @Unique @Borrowed Holder, n: Int) {
    var i = 0
    while (i < n) {
        i = i + 1
        borrowThen(h.inner) {
            if (i == 3) continue
        }
    }
}
