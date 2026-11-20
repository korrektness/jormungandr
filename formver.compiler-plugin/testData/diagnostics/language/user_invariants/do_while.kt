// FULL_JDK
// REPLACE_STDLIB_EXTENSIONS

import org.jetbrains.kotlin.formver.plugin.*

// The body runs once before the condition is first evaluated.
fun <!VIPER_TEXT!>runsOnce<!>() {
    var i = 10
    do {
        loopInvariants { i == 10 || i == 11 }
        i = 11
    } while (i < 5)
    verify(i == 11)
}

fun <!VIPER_TEXT!>counts<!>(n: Int) {
    preconditions { 1 <= n && n <= 100 }
    var i = 0
    do {
        loopInvariants { 0 <= i && i <= n }
        if (i < n) i = i + 1
    } while (i < n)
    verify(i == n)
}

// The condition reads a local of the body.
fun <!VIPER_TEXT!>conditionReadsBodyLocal<!>(n: Int) {
    preconditions { 0 <= n && n <= 100 }
    var i = 0
    do {
        loopInvariants { 0 <= i && i <= n + 1 }
        val next = if (i <= n) i + 1 else i
        i = next
    } while (next <= n)
    verify(i == n + 1)
}

// `continue` evaluates the condition.
fun <!VIPER_TEXT!>continueEvaluatesCondition<!>() {
    var i = 0
    do {
        loopInvariants { 0 <= i && i <= 10 }
        if (i < 10) i++
        if (i < 5) continue
        refute(false)
    } while (i < 10)
    verify(i == 10)
}

fun <!VIPER_TEXT!>withBreak<!>() {
    var i = 0
    do {
        loopInvariants { 0 <= i && i <= 3 }
        if (i == 3) break
        i++
    } while (true)
    verify(i == 3)
}

fun <!VIPER_TEXT!>fill<!>(arr: @Unique @Borrowed IntArray, v: Int) {
    preconditions { arr.size > 0 }
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == v) }
    }
    var i = 0
    do {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { k -> (0 <= k && k < i) implies (arr[k] == v) }
        }
        if (i < arr.size) {
            arr[i] = v
            i++
        }
    } while (i < arr.size)
}

fun <!VIPER_TEXT!>runsOnlyOnce<!>() {
    var i = 10
    do {
        loopInvariants { i == 10 || i == 11 }
        i = 11
    } while (i < 5)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>i == 10<!>)
}

fun <!VIPER_TEXT!>invariantBeforeFirstRun<!>() {
    var i = 5
    do {
        loopInvariants { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>i < 5<!> }
        i = 0
    } while (false)
}

fun <!VIPER_TEXT!>invariantAfterContinuingRun<!>() {
    var i = 0
    do {
        loopInvariants { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>i < 1<!> }
        i = i + 1
    } while (i < 2)
}

// Only the last run, whose condition fails, breaks the invariant.
fun <!VIPER_TEXT!>invariantAfterLastRun<!>() {
    var i = 0
    do {
        loopInvariants { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>i < 2<!> }
        i = i + 1
    } while (i < 2)
}
