// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>finallyNormal<!>() {
    var x = 0
    try { x = 1 } finally { verify(x == 1); x = 2 }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyReturnSnapshot<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 1
    try { return x } finally { x = 2 }
}

fun <!VIPER_TEXT!>finallyOverridesReturn<!>(): Int {
    postconditions<Int> { it == 2 }
    try { return 1 } finally { return 2 }
}

fun <!VIPER_TEXT!>finallyOverridesThrow<!>(): Int {
    postconditions<Int> { it == 2 }
    try { throw IllegalArgumentException() } finally { return 2 }
}

fun <!VIPER_TEXT!>finallyThrowOverridesReturn<!>(): Int {
    postconditions<Int> { it == 2 }
    try {
        try { return 1 } finally { throw IllegalArgumentException() }
    } catch (e: Exception) { return 2 }
}

fun <!VIPER_TEXT!>finallyBeforeOuterCatch<!>() {
    var x = 0
    try {
        try { x = 1; throw IllegalArgumentException() } finally { x = 2 }
    } catch (e: Exception) { verify(x == 2) }
}

fun <!VIPER_TEXT!>finallyAfterCatchThrow<!>() {
    var x = 0
    try {
        try { throw IllegalArgumentException() }
        catch (e: Exception) { x = 1; throw e }
        finally { x = 2 }
    } catch (e: Exception) { verify(x == 2) }
}

fun <!VIPER_TEXT!>finallyCatchReturn<!>(): Int {
    postconditions<Int> { it == 2 }
    try { throw IllegalArgumentException() }
    catch (e: Exception) { return 1 }
    finally { return 2 }
}

fun <!VIPER_TEXT!>finallyNestedReturn<!>(): Int {
    postconditions<Int> { it == 3 }
    try {
        try { return 1 } finally { return 2 }
    } finally { return 3 }
}

fun <!VIPER_TEXT!>finallyNestedPendingReturn<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 0
    try {
        try { return 1 } finally { x = 2 }
    } finally { verify(x == 2) }
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>finallyOverrideIsNotVacuous<!>(): Int {
    postconditions<Int> { it == 1 }
    try { throw IllegalArgumentException() } finally { return 2 }
}<!>

fun <!VIPER_TEXT!>finallyNestedOrder<!>() {
    var x = 0
    try {
        try { x = 1 } finally { verify(x == 1); x = 2 }
    } finally { verify(x == 2); x = 3 }
    verify(x == 3)
}

fun <!VIPER_TEXT!>finallyExpressionSnapshot<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 1
    return try { x } finally { x = 2 }
}

fun <!VIPER_TEXT!>catchExpressionValue<!>(): Int {
    postconditions<Int> { it == 2 }
    return try { throw IllegalArgumentException() } catch (e: Exception) { 2 }
}

fun <!VIPER_TEXT!>finallyBreak<!>() {
    var x = 0
    while (true) {
        try { break } finally { x = 2 }
    }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyContinue<!>() {
    var x = 0
    while (x == 0) {
        loopInvariants { x == 0 || x == 2 }
        try { continue } finally { x = 2 }
    }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyOverridesBreak<!>(): Int {
    postconditions<Int> { it == 2 }
    while (true) {
        try { break } finally { return 2 }
    }
    return 1
}

fun <!VIPER_TEXT!>finallyContinueOverridesBreak<!>() {
    var x = 0
    while (x == 0) {
        loopInvariants { x == 0 || x == 2 }
        try { break } finally { x = 2; continue }
    }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyInternalLoop<!>() {
    var x = 0
    try {
        while (true) { break }
        verify(x == 0)
    } finally { x = 2 }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyInlineLocalReturn<!>() {
    var x = 0
    try {
        run { return@run 1 }
        verify(x == 0)
    } finally { x = 2 }
    verify(x == 2)
}

fun <!VIPER_TEXT!>finallyInlineNonlocalReturn<!>(): Int {
    postconditions<Int> { it == 2 }
    try { run { return 1 } } finally { return 2 }
}

fun <!VIPER_TEXT!>finallyCallStates<!>(p: Boolean) {
    var x = 0
    try {
        if (p) { x = 1; unknownFinallyCall() }
        x = 2
    } finally {
        verify(x == 1 || x == 2)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 2<!>)
    }
}

fun <!VIPER_TEXT!>unknownFinallyCall<!>() { }

fun <!VIPER_TEXT!>finallyCheckStates<!>() {
    var x = 0
    try { x = 1; check(false); x = 2 }
    finally {
        verify(x == 1)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 2<!>)
    }
}

fun <!VIPER_TEXT!>finallyRequireStates<!>() {
    var x = 0
    try { x = 1; require(false); x = 2 }
    finally {
        verify(x == 1)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 2<!>)
    }
}

fun <!VIPER_TEXT!>finallyNonNullStates<!>(p: Int?) {
    var x = 0
    try { x = 1; p!!; x = 2 }
    finally {
        verify(x == 1 || x == 2)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 2<!>)
    }
}

fun <!VIPER_TEXT!>finallyCastStates<!>(p: Any) {
    var x = 0
    try { x = 1; p as String; x = 2 }
    finally {
        verify(x == 1 || x == 2)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 2<!>)
    }
}

fun <!VIPER_TEXT!>finallyNestedInCatch<!>(): Int {
    postconditions<Int> { it == 3 }
    var x = 0
    try {
        try { throw IllegalArgumentException() }
        catch (e: Exception) {
            try { x = 1; throw e } finally { x = 2 }
        }
    } catch (e: Exception) {
        verify(x == 0 || x == 2)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 0<!>)
        x = 3
    }
    return x
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>finallyNestedCatchIsReachable<!>(): Int {
    postconditions<Int> { it == 0 }
    try {
        try { throw IllegalArgumentException() }
        catch (e: Exception) {
            try { throw e } finally { verify(true) }
        }
    } catch (e: Exception) { return 1 }
}<!>

fun <!VIPER_TEXT!>finallyBoundsAreErrors<!>(a: IntArray) {
    try { <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[-1]<!> } finally { verify(true) }
}

fun <!VIPER_TEXT!>finallyDivisionIsError<!>(x: Int) {
    try { <!DIVISION_BY_ZERO, DIVISION_BY_ZERO!>x / 0<!> } finally { verify(true) }
}
