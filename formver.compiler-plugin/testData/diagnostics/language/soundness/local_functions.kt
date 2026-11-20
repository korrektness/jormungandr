// FULL_JDK
// FULL_FILE_VERIFICATION

import org.jetbrains.kotlin.formver.plugin.*

// A local function is verified as a declaration of its own (docs/soundness.md, "Skipped declarations").

fun <!VIPER_TEXT!>host<!>(): Int {
    fun <!VIPER_TEXT!>local<!>(): Int {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
        return 0
    }
    return local()
}
