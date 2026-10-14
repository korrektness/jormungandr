// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// A literal argument must support the same proofs as a string parameter with the same characters.

fun <!VIPER_TEXT!>endsWithChars<!>(s: String, suffix: String): Boolean {
    postconditions<Boolean> { r ->
        r == (suffix.length <= s.length &&
            forAll<Int> { (0 <= it && it < suffix.length) implies (s[s.length - suffix.length + it] == suffix[it]) })
    }
    if (suffix.length > s.length) return false
    val offset = s.length - suffix.length
    var i = 0
    while (i < suffix.length) {
        loopInvariants {
            0 <= i && i <= suffix.length
            forAll<Int> { (0 <= it && it < i) implies (s[offset + it] == suffix[it]) }
        }
        if (s[offset + i] != suffix[i]) return false
        i = i + 1
    }
    return true
}

fun <!VIPER_TEXT!>literalArgument<!>(s: String): Boolean {
    preconditions { s.length == 1 && s[0] == '{' }
    postconditions<Boolean> { r -> r == false }
    return endsWithChars(s, "}")
}
