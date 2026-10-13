// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// No total function satisfies `loopy(x) == loopy(x) + 1`, so `loopy` is rejected and its callers see no definition.
@Pure fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>loopy<!>(x: Int): Int = loopy(x) + 1

fun <!VIPER_TEXT!>loopyFalse<!>() {
    val y = loopy(0)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

@Pure fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>flip<!>(x: Int): Boolean = !flip(x)

fun <!VIPER_TEXT!>flipFalse<!>() {
    val y = flip(0)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

@Pure fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isEven<!>(n: Int): Boolean = if (n <= 0) true else isOdd(n - 1)

@Pure fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isOdd<!>(n: Int): Boolean = if (n <= 0) false else isEven(n - 1)

// The law reaches `loopy` through the predicate of `Cell`, which `cellFalse` unfolds.
<!VIPER_TEXT!>class Cell<!>(var n: Int) {
    @Law @Pure fun valid(): Boolean = loopy(n) == loopy(n)
}

fun <!VIPER_TEXT!>cellFalse<!>(c: @Unique @Borrowed Cell) {
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

// The measure grows on the recursive call.
@Pure fun <!VIPER_TEXT!>growing<!>(x: Int): Int {
    preconditions { decreases(x) }
    return if (x == 0) 0 else <!TERMINATION_NOT_ESTABLISHED!>growing(x + 1)<!>
}

@Pure fun <!VIPER_TEXT!>countdown<!>(x: Int): Int {
    preconditions {
        x >= 0
        decreases(x)
    }
    return if (x == 0) 0 else 1 + countdown(x - 1)
}

fun <!VIPER_TEXT!>countdownClient<!>() {
    verify(countdown(0) == 0)
    refute(false)
}

// Silicon does not assume the postconditions of a mutually recursive callee, so neither function establishes that it
// returns a Boolean.
<!VIPER_VERIFICATION_ERROR!>@Pure fun <!VIPER_TEXT!>even<!>(n: Int): Boolean {
    preconditions { decreases(n) }
    return if (n <= 0) true else odd(n - 1)
}<!>

<!VIPER_VERIFICATION_ERROR!>@Pure fun <!VIPER_TEXT!>odd<!>(n: Int): Boolean {
    preconditions { decreases(n) }
    return if (n <= 0) false else even(n - 1)
}<!>

@Pure fun <!VIPER_TEXT!>ackermann<!>(m: Int, n: Int): Int {
    preconditions {
        m >= 0
        n >= 0
        decreases(m, n)
    }
    postconditions<Int> { r -> r >= 0 }
    return if (m == 0) n + 1 else if (n == 0) ackermann(m - 1, 1) else ackermann(m - 1, ackermann(m, n - 1))
}

fun <!VIPER_TEXT!>ackermannClient<!>() {
    verify(ackermann(0, 3) == 4)
    refute(false)
}

class Node(val value: Int, val next: @Unique Node?)

@Pure fun <!VIPER_TEXT!>length<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    return if (n == null) 0 else 1 + length(n.next)
}

fun <!VIPER_TEXT!>lengthClient<!>(n: @Unique Node) {
    verify(length(n) >= 1 || length(n) <= 1)
    refute(false)
}

fun <!VERIFICATION_SKIPPED!>impureMeasure<!>(x: Int) {
    preconditions { <!INVALID_TERMINATION_MEASURE!>decreases(x)<!> }
}

@Pure fun <!VERIFICATION_SKIPPED!>stringMeasure<!>(s: String): Int {
    preconditions { decreases(<!INVALID_TERMINATION_MEASURE!>s<!>) }
    return 0
}

@Pure fun <!VERIFICATION_SKIPPED!>twoMeasures<!>(x: Int): Int {
    preconditions {
        decreases(x)
        <!INVALID_TERMINATION_MEASURE!>decreases(x)<!>
    }
    return 0
}
