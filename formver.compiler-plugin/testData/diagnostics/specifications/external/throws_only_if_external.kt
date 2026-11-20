// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/testData/support/specifications/externalThrowsSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/externalLibrary.kt
import org.jetbrains.kotlin.formver.plugin.*
import external.library.*

fun <!VIPER_TEXT!>digitOrThrow<!>(d: Int): Int {
    postconditions<Int> { result -> result == d }
    throwsOnlyIf { d < 0 || d > 9 }
    return checkedDigit(d)
}

fun <!VIPER_TEXT!>digitTwice<!>(d: Int): Int {
    throwsOnlyIf { d < 0 || d > 9 }
    checkedDigit(d)
    return checkedDigit(d)
}

fun <!VIPER_TEXT!>digitTooStrict<!>(d: Int): Int {
    throwsOnlyIf { d < 0 }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>checkedDigit(d)<!>
}

fun <!VIPER_TEXT!>unclausedLibraryCall<!>(d: Int): Int {
    throwsOnlyIf { d < 0 }
    if (d < 0) return 0
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>uncheckedDigit(d)<!>
}
