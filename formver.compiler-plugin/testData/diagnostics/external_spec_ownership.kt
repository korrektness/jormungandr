// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalCounterSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>rejectedBorrow<!>(counter: Counter) { <!UNIQUENESS_MISMATCH!>counter<!>.advance(1) }

<!INVALID_EXTERNAL_SPEC!><!VIPER_TEXT!>class SourceGauge<!> : Gauge { <!INVALID_OBSERVER, OVERRIDE_UNIQUENESS_MISMATCH!>override fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>read<!>(): Int = 1<!> }<!>
<!INVALID_EXTERNAL_SPEC!><!VIPER_TEXT!>class IndirectGauge<!> : DefaultGauge()<!>
