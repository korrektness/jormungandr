// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// `List.get` inside a quantifier body goes through a method-call embedding path, unlike
// `String`'s indexing operator. A quantifier body is lowered to a Viper expression, which cannot
// hold a method call, so this is reported as an unsupported construct.
fun existsListGetCrash(l: List<Int>, res: Int): Int {
    postconditions<Int> {
        exists<Int> { i -> 0 <= i && i < l.size && <!UNSUPPORTED_FEATURE!>l[i]<!> == l[res] }
    }
    return 0
}
