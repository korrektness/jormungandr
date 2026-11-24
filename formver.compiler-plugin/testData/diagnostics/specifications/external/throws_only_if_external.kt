// FULL_JDK
// WITH_STDLIB
// DIAGNOSTIC_KINDS
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/testData/support/specifications/externalThrowsSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/externalLibrary.kt
import java.io.File
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

fun <!VIPER_TEXT!>modeledTrimCall<!>(text: String): String = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>text.trim()<!>

fun <!VERIFICATION_SKIPPED!>modeledTrimReference<!>(text: String): String = with(text, <!UNSUPPORTED_FEATURE!>String::trim<!>)

fun <!VIPER_TEXT!>modeledFileCall<!>(path: String): File {
    throwsOnlyIf { false }
    return <!THROWS_CONDITION_NOT_ESTABLISHED!>File(path)<!>
}

fun <!VERIFICATION_SKIPPED!>modeledFileReference<!>(path: String): File = with(path, <!UNSUPPORTED_FEATURE!>::File<!>)

fun <!VIPER_TEXT!>scopeTarget<!>(text: String?): String? = text

fun <!VERIFICATION_SKIPPED!>modeledScopeReference<!>(text: String): String? =
    <!UNSUPPORTED_FEATURE!>text.let(block = ::scopeTarget)<!>

fun <!VERIFICATION_SKIPPED!>modeledScopeAlias<!>(text: String): String? {
    val ref = ::scopeTarget
    return <!UNSUPPORTED_FEATURE!>text.let(block = ref)<!>
}
