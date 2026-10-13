// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// A local function is verified as a declaration of its own (docs/soundness.md, "Skipped declarations").

fun <!VERIFICATION_SKIPPED!>host<!>(): Int {
    <!UNSUPPORTED_FEATURE!>fun <!VIPER_TEXT!>local<!>(): Int {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
        return 0
    }<!>
    return local()
}
