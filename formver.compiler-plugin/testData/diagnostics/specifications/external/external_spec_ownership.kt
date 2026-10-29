// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/support/specifications/externalCounterSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>sharedAdvance<!>(counter: Counter) { <!UNCHECKED_LIBRARY_CALL!>counter.advance(1)<!> }

<!INVALID_EXTERNAL_SPEC!><!VIPER_TEXT!>class SourceGauge<!> : Gauge { <!INVALID_OBSERVER, OVERRIDE_UNIQUENESS_MISMATCH!>override fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>read<!>(): Int = 1<!> }<!>
<!INVALID_EXTERNAL_SPEC!><!VIPER_TEXT!>class IndirectGauge<!> : DefaultGauge()<!>
