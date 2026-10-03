// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

inline fun <!VIPER_TEXT!>myEach<!>(s: String, f: (Char) -> Unit) {
    var i = 0
    while (i < s.length) {
        loopInvariants { i >= 0 }
        f(s[i])
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>userInline<!>(s: String): Int {
    var n = 0
    myEach(s) { c -> if (c == 'a') n++ }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>stdlibForEach<!>(s: String) {
    s.forEach { c -> if (c == 'a') throw IllegalArgumentException() }
}

@AlwaysVerify
fun <!VIPER_TEXT!>stdlibCount<!>(s: String): Int = s.count { it == 'a' }
