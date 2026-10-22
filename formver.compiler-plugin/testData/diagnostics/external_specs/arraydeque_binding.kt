// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>emptyWorklist<!>() {
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    verify(queue.size == 0)
}

fun <!VIPER_TEXT!>wrongEmptyWorklist<!>() {
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>queue.size == 1<!>)
}

fun <!VIPER_TEXT!>peekEmpty<!>() {
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    verify(<!VIPER_VERIFICATION_ERROR!>queue[0]<!> == 0)
}

fun <!VERIFICATION_SKIPPED!>unboundCapacity<!>() {
    <!UNSUPPORTED_FEATURE!>ArrayDeque<Int>(4)<!>
}

fun <!VERIFICATION_SKIPPED!>unboundMember<!>(queue: @Unique ArrayDeque<Int>) {
    <!UNSUPPORTED_FEATURE!>queue.removeAt(0)<!>
}

fun <!VIPER_TEXT!>enqueueOnce<!>(queue: @Unique @Borrowed ArrayDeque<Int>, vertex: Int) {
    preconditions { queue.size < 715827882 }
    if (!queue.contains(vertex)) {
        queue.addLast(vertex)
        val last = queue.last()
        verify(last == vertex)
    }
}

fun <!VIPER_TEXT!>enqueueWithoutBound<!>(queue: @Unique @Borrowed ArrayDeque<Int>, vertex: Int) {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>queue.addLast(vertex)<!>
}

fun <!VIPER_TEXT!>borrowedSizeBound<!>(queue: @Unique @Borrowed ArrayDeque<Int>) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>queue.size <= 1073741824<!>)
}

fun <!VIPER_TEXT!>enqueueOnceFalseGrowth<!>(queue: @Unique @Borrowed ArrayDeque<Int>, vertex: Int) {
    preconditions { queue.size < 715827882 }
    val before = queue.size
    if (!queue.contains(vertex)) queue.addLast(vertex)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>queue.size == before + 1<!>)
}
