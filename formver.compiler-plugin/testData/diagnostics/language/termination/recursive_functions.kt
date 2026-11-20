// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>countDown<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else countDown(n - 1)
}

// The measure stays equal: `stuck` calls itself with its own argument.
fun <!VIPER_TEXT!>stuck<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else <!TERMINATION_NOT_ESTABLISHED!>stuck(n)<!>
}

// Without `n >= 0` the measure is not bounded below: from a negative `n` the recursion does not stop.
fun <!VIPER_TEXT!>unbounded<!>(n: Int): Int {
    preconditions {
        n > Int.MIN_VALUE
        decreases(n)
    }
    return if (n == 0) 0 else <!TERMINATION_NOT_ESTABLISHED!>unbounded(n - 1)<!>
}

// The first measure decreases, or stays equal while the second decreases.
fun <!VIPER_TEXT!>lexicographic<!>(m: Int, n: Int): Int {
    preconditions {
        m >= 0
        n >= 0
        decreases(m, n)
    }
    return if (m == 0) 0 else if (n == 0) lexicographic(m - 1, 10) else lexicographic(m, n - 1)
}

fun <!VIPER_TEXT!>isEven<!>(n: Int): Boolean {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) true else isOdd(n - 1)
}

fun <!VIPER_TEXT!>isOdd<!>(n: Int): Boolean {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) false else isEven(n - 1)
}

// `ping` decreases the measure on its call of `pong`, but `pong` passes it on unchanged.
fun <!VIPER_TEXT!>ping<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else pong(n - 1)
}

fun <!VIPER_TEXT!>pong<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return <!TERMINATION_NOT_ESTABLISHED!>ping(n)<!>
}

// `dong` closes the cycle of `ding` without a measure of its own.
fun <!VIPER_TEXT!>ding<!>(n: Int): Int {
    preconditions { decreases(n) }
    return <!TERMINATION_NOT_ESTABLISHED!>dong(n)<!>
}

fun <!VIPER_TEXT!>dong<!>(n: Int): Int = if (n <= 0) 0 else ding(n - 1)

// A lambda's body is verified on its own, where the decrease is not checked.
fun <!VIPER_TEXT!>recursesInLambda<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else run { <!TERMINATION_NOT_ESTABLISHED!>recursesInLambda(n - 1)<!> }
}

// `shared` is in the cycles of both `left` and `right`, so each has its own check of a call of `shared`.
fun <!VIPER_TEXT!>left<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else shared(n - 1)
}

fun <!VIPER_TEXT!>right<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n, 0)
    }
    return if (n == 0) 0 else shared(n - 1)
}

fun <!VIPER_TEXT!>shared<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    if (n == 0) return 0
    left(n - 1)
    return right(n - 1)
}

open <!VIPER_TEXT!>class Base<!> {
    open fun <!VIPER_TEXT!>g<!>(n: Int): Int {
        preconditions {
            n >= 0
            decreases(n)
        }
        return if (n == 0) 0 else viaOverride(n - 1, this)
    }
}

<!VIPER_TEXT!>class Derived<!> : Base() {
    override fun <!VIPER_TEXT!>g<!>(n: Int): Int = viaOverride(<!INTEGER_OVERFLOW!>n + 1<!>, this)
}

// An override of `g` may call back with a larger measure, so the call of an open member is rejected even in the cycle.
fun <!VIPER_TEXT!>viaOverride<!>(n: Int, b: Base): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else <!TERMINATION_NOT_ESTABLISHED!>b.g(n - 1)<!>
}

// The call of `inlinedBack` is inlined, so no check of its measure is emitted at it.
fun <!VIPER_TEXT!>callsInlined<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return <!TERMINATION_NOT_ESTABLISHED!>inlinedBack(<!INTEGER_OVERFLOW!>n + 5<!>)<!>
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>inlinedBack<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else callsInlined(n - 1)
}
