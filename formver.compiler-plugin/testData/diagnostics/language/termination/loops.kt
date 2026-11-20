// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// A loop variant is checked also in a function without a measure.
fun <!VIPER_TEXT!>countUp<!>(n: Int): Int {
    var i = 0
    while (i < n) {
        loopInvariants {
            i >= 0
            decreases(n - i)
        }
        i++
    }
    return i
}

// On odd `i` the variant stays equal.
fun <!VIPER_TEXT!>stalls<!>(n: Int) {
    var i = 0
    <!TERMINATION_NOT_ESTABLISHED!>while (i < n) {
        loopInvariants {
            i >= 0
            decreases(n - i)
        }
        if (i % 2 == 0) i++
    }<!>
}

// The jump back from `continue` does not decrease the variant.
fun <!VIPER_TEXT!>skips<!>(n: Int) {
    var i = 0
    <!TERMINATION_NOT_ESTABLISHED!>while (i < n) {
        loopInvariants {
            i >= 0
            decreases(n - i)
        }
        if (i == 3) continue
        i++
    }<!>
}

// The inner loop exits through `break`; each outer iteration decreases the outer variant.
fun <!VIPER_TEXT!>nested<!>(n: Int) {
    preconditions {
        n >= 0
        decreases()
    }
    var i = 0
    while (i < n) {
        loopInvariants {
            0 <= i && i <= n
            decreases(n - i)
        }
        var j = n
        while (true) {
            loopInvariants { decreases(j) }
            if (j <= i) break
            j--
        }
        i++
    }
}

// The lexicographic variant of two counters.
fun <!VIPER_TEXT!>twoCounters<!>(m: Int, n: Int) {
    preconditions {
        m >= 0
        n >= 0
    }
    var a = m
    var b = n
    while (a > 0) {
        loopInvariants {
            b >= 0
            decreases(a, b)
        }
        if (b > 0) b-- else {
            a--
            b = n
        }
    }
}

fun <!VIPER_TEXT!>missingVariant<!>(n: Int) {
    preconditions { decreases() }
    var i = 0
    <!MISSING_TERMINATION_MEASURE!>while (i < n) {
        i++
    }<!>
}

// A `for` over a range terminates by construction.
fun <!VIPER_TEXT!>ranged<!>(n: Int): Int {
    preconditions { decreases() }
    var last = 0
    for (i in 0 until n) {
        last = i
    }
    return last
}

fun <!VIPER_TEXT!>booleanVariant<!>(b: Boolean) {
    var go = b
    while (go) {
        loopInvariants { decreases(<!INVALID_TERMINATION_MEASURE!>go<!>) }
        go = false
    }
}

// A function without a measure needs no variant, also after a termination-checked function.
fun <!VIPER_TEXT!>uncheckedLoop<!>(n: Int) {
    var i = 0
    while (i < n) i++
}

fun <!VIPER_TEXT!>twoVariants<!>(n: Int) {
    var i = 0
    while (i < n) {
        loopInvariants {
            i >= 0
            decreases(n - i)
            <!INVALID_TERMINATION_MEASURE!>decreases(i)<!>
        }
        i++
    }
}

fun <!VIPER_TEXT!>countsDownOnce<!>(n: Int) {
    preconditions { decreases() }
    var i = n
    do {
        loopInvariants { decreases(i) }
        if (i > 0) i--
    } while (i > 0)
}

fun <!VIPER_TEXT!>repeatsForever<!>(n: Int) {
    preconditions { decreases() }
    var i = n
    <!TERMINATION_NOT_ESTABLISHED!>do {
        loopInvariants { decreases(i) }
    } while (i > 0)<!>
}
