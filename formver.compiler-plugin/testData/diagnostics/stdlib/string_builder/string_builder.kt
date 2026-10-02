// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>repeatChar<!>(c: Char, n: Int): String {
    preconditions {
        n >= 0
    }
    postconditions<String> { r ->
        r.length == n
    }
    val sb: @Unique StringBuilder = StringBuilder()
    var i = 0
    while (i < n) {
        loopInvariants {
            0 <= i && i <= n
            sb.length == i
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

@AlwaysVerify
fun <!VIPER_TEXT!>literal<!>(): String {
    postconditions<String> { r ->
        r == "abc"
    }
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append('a').append("bc")
    return sb.toString()
}

@AlwaysVerify
fun <!VIPER_TEXT!>appendTwice<!>(sb: @Unique @Borrowed StringBuilder, s: String) {
    postconditions<Unit> {
        sb.length == old(sb.length) + 2 * s.length
        sb.toString() == old(sb.toString()) + s + s
    }
    sb.append(s)
    sb.append(s)
}

@AlwaysVerify
fun <!VIPER_TEXT!>clearThenAppend<!>(sb: @Unique @Borrowed StringBuilder) {
    postconditions<Unit> {
        sb.toString() == "x"
    }
    sb.clear().append('x')
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnAppended<!>(sb: @Unique StringBuilder): @Unique StringBuilder {
    return sb.append('!')
}

@AlwaysVerify
fun <!VIPER_TEXT!>bindAppended<!>(): Int {
    postconditions<Int> { r ->
        r == 2
    }
    val sb: @Unique StringBuilder = StringBuilder()
    val t: @Unique StringBuilder = sb.append('x')
    t.append('y')
    return t.length
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongLength<!>() {
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append('a')
    verify(<!VIPER_VERIFICATION_ERROR!>sb.length == 2<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>appendShared<!>(sb: StringBuilder) {
    sb.append('a')
    val n = sb.length
}

fun <!VERIFICATION_SKIPPED!>readSharedInSpec<!>(sb: StringBuilder): Int {
    postconditions<Int> { r -> r == <!UNSUPPORTED_OWNERSHIP!>sb.length<!> }
    return 0
}
