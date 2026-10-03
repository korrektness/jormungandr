// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>hoistedInvariants<!>(s: String) {
    var n = 0
    s.forEachIndexed { i, c ->
        loopInvariants {
            n >= 0
            n <= i
        }
        if (c == 'a') n++
    }
    verify(n <= s.length)
}

@AlwaysVerify
fun <!VIPER_TEXT!>indexFacts<!>(s: String): Int {
    var last = -1
    s.forEachIndexed { i, _ ->
        loopInvariants { last < i }
        verify(0 <= i, i < s.length)
        last = i
    }
    return last
}

@AlwaysVerify
fun <!VIPER_TEXT!>previousCharacter<!>(s: String): Int {
    var escaped = 0
    s.forEachIndexed { i, c ->
        if (c == '"' && (i == 0 || s[i - 1] != '\\')) escaped++
    }
    return escaped
}

@AlwaysVerify
fun <!VIPER_TEXT!>countBounds<!>(s: String) {
    val n = s.count { it == 'a' }
    verify(0 <= n, n <= s.length)
}

@AlwaysVerify
fun <!VIPER_TEXT!>countIsNotExact<!>(s: String) {
    preconditions { s.length > 0 }
    val n = s.count { true }
    verify(<!VIPER_VERIFICATION_ERROR!>n == s.length<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>labeledReturn<!>(s: String): Int {
    var n = 0
    s.forEach {
        loopInvariants { n >= 0 }
        if (it == ' ') return@forEach
        n++
    }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>throwInside<!>(s: String) {
    s.forEach { if (it == '\n') throw IllegalArgumentException() }
}

@AlwaysVerify
fun <!VIPER_TEXT!>repeatIndex<!>(times: Int): Int {
    var sum = 0
    repeat(times) { i ->
        loopInvariants { sum >= 0 }
        verify(i >= 0)
        sum += i
    }
    return sum
}

@AlwaysVerify
fun <!VIPER_TEXT!>fillOwnedBuilder<!>(s: String): String {
    postconditions<String> { r -> r.length == s.length }
    val sb: @Unique StringBuilder = StringBuilder()
    s.forEachIndexed { i, c ->
        loopInvariants { sb.length == i }
        sb.append(c)
    }
    return sb.toString()
}

inline fun <!VIPER_TEXT!>myEach<!>(s: String, f: (Char) -> Unit) {
    var i = 0
    while (i < s.length) {
        loopInvariants { i >= 0 }
        f(s[i])
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>myEachOwnedBuilder<!>(s: String): String {
    val sb: @Unique StringBuilder = StringBuilder()
    myEach(s) { sb.append(it) }
    return sb.toString()
}

fun <!VERIFICATION_SKIPPED!>invariantOverElement<!>(s: String) {
    var n = 0
    s.forEach { <!UNSUPPORTED_FEATURE!>c<!> ->
        loopInvariants { c != 'x' }
        n++
    }
}

fun <!VERIFICATION_SKIPPED!>charSequenceReceiver<!>(s: CharSequence) {
    var n = 0
    <!UNSUPPORTED_FEATURE!>s.forEach { n++ }<!>
}
