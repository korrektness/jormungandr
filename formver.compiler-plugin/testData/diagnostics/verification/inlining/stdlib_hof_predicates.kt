// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>anyWitness<!>(s: String, k: Int) {
    preconditions { 0 <= k && k < s.length }
    if (s.any { it == 'a' }) {
        verify(s.length > 0)
    } else {
        verify(s[k] != 'a')
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>allDigits<!>(s: String, k: Int) {
    preconditions { 0 <= k && k < s.length }
    if (s.all { it >= '0' && it <= '9' }) {
        verify(s[k] >= '0')
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>noneSpace<!>(s: String, k: Int) {
    preconditions { 0 <= k && k < s.length }
    if (s.none { it == ' ' }) {
        verify(s[k] != ' ')
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>firstIndex<!>(s: String): Int {
    postconditions<Int> { r -> -1 <= r && r < s.length }
    val r = s.indexOfFirst { it == '=' }
    if (r >= 0) {
        verify(s[r] == '=')
    }
    return r
}

@AlwaysVerify
fun <!VIPER_TEXT!>firstIndexIsFirst<!>(s: String, k: Int) {
    preconditions { 0 <= k && k < s.length && s[k] == '=' }
    val r = s.indexOfFirst { it == '=' }
    verify(0 <= r, r <= k)
}

@AlwaysVerify
fun <!VIPER_TEXT!>firstMatch<!>(s: String) {
    val c = s.firstOrNull { it == 'x' || it == 'y' }
    if (c != null) {
        verify(c == 'x' || c == 'y')
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>labeledReturnPredicate<!>(s: String): Boolean = s.any { return@any it == 'q' }

@AlwaysVerify
fun <!VIPER_TEXT!>impureLambdaBounds<!>(s: String) {
    var calls = 0
    val r = s.indexOfFirst {
        loopInvariants { calls >= 0 }
        calls++
        it == 'z'
    }
    verify(-1 <= r, r < s.length)
}

@AlwaysVerify
fun <!VIPER_TEXT!>impureLambdaNoFacts<!>(s: String) {
    var calls = 0
    val r = s.indexOfFirst {
        calls++
        it == 'z'
    }
    if (r >= 0) {
        verify(<!VIPER_VERIFICATION_ERROR!>s[r] == 'z'<!>)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>ownedArraySearch<!>(n: Int, k: Int): Int {
    preconditions { 0 <= k && k < n }
    val a: @Unique IntArray = IntArray(n) { it }
    if (a.all { it >= 0 }) {
        verify(a[k] >= 0)
    }
    val r = a.indexOfFirst { it == 5 }
    if (r >= 0) {
        verify(a[r] == 5)
    }
    val f = a.firstOrNull { it > 3 }
    if (f != null) {
        verify(f > 3)
    }
    var sum = 0
    a.forEach {
        loopInvariants { sum >= 0 }
        if (it > 0) sum += it
    }
    a.forEachIndexed { i, _ -> a[i] = 0 }
    return a.count { it > 0 } + sum
}

@AlwaysVerify
fun <!VIPER_TEXT!>sharedArraySearch<!>(a: IntArray): Boolean {
    val r = a.indexOfFirst { it == 0 }
    verify(-1 <= r, r < a.size)
    return a.none { it < 0 }
}

@AlwaysVerify
fun <!VIPER_TEXT!>sharedArrayNoFacts<!>(a: IntArray) {
    if (a.all { it == 7 }) {
        val none = a.none { it != 7 }
        verify(<!VIPER_VERIFICATION_ERROR!>none<!>)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>listCallsWithFunctionValues<!>(xs: List<Int>, p: (Int) -> Boolean, f: (Int) -> Unit): Int {
    xs.forEach(f)
    return if (xs.any(p)) xs.count(p) else 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>arrayAndStringCallsWithFunctionValues<!>(a: IntArray, s: String, p: (Int) -> Boolean, q: (Char) -> Boolean): Boolean =
    a.any(p) && s.any(q)

inline fun <!VIPER_TEXT!>anyOf<!>(a: IntArray, p: (Int) -> Boolean): Boolean = a.any(p)

@AlwaysVerify
fun <!VIPER_TEXT!>inlineParameterSearch<!>(a: IntArray): Boolean = anyOf(a) { it == 3 }
