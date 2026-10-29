// FULL_JDK
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

// An Iterable that is not a Collection may have more than `Int.MAX_VALUE` elements, where `visited` wraps negative.
fun <!VERIFICATION_SKIPPED!>countVisited<!>(values: @Unique @Borrowed Iterable<Int>): Int {
    postconditions<Int> { it >= 0 }
    var visited = 0
    <!UNSUPPORTED_FEATURE!>values<!>.forEach { loopInvariants<Int> { prefix -> visited == prefix.size }; visited++ }
    return visited
}

fun <!VERIFICATION_SKIPPED!>iterableObserver<!>(values: @Unique @Borrowed Iterable<Int>): Boolean {
    postconditions<Boolean> { it == (iterationSequence(<!UNSUPPORTED_FEATURE!>values<!>).size >= 0) }
    return true
}

fun <!VIPER_TEXT!>countVisitedList<!>(values: @Unique @Borrowed List<Int>): Int {
    postconditions<Int> { it >= 0 }
    var visited = 0
    values.forEach { loopInvariants<Int> { prefix -> visited == prefix.size }; visited++ }
    return visited
}

fun <!VIPER_TEXT!>countVisitedSet<!>(values: @Unique @Borrowed Set<Int>): Int {
    postconditions<Int> { it == values.size }
    var visited = 0
    values.forEach { loopInvariants<Int> { prefix -> visited == prefix.size }; visited++ }
    return visited
}
