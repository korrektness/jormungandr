// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// `List.get` on a shared list is the opaque library call, whose result is a value the verifier knows nothing about.
// A quantifier body is lowered to a Viper expression, which cannot introduce one.
fun <!VERIFICATION_SKIPPED!>existsListGetCrash<!>(l: List<Int>, res: Int): Int {
    postconditions<Int> {
        exists<Int> { i -> 0 <= i && i < <!UNCHECKED_LIBRARY_CALL, UNSUPPORTED_FEATURE!>l.size<!> && <!UNCHECKED_LIBRARY_CALL!>l[i]<!> == <!UNCHECKED_LIBRARY_CALL!>l[res]<!> }
    }
    return 0
}

fun <!VIPER_TEXT!>existsUniqueListGet<!>(l: @Unique @Borrowed List<Int>): Int {
    postconditions<Int> {
        l.size == 0 || exists<Int> { i -> 0 <= i && i < l.size && l[i] == l[0] }
    }
    return 0
}
