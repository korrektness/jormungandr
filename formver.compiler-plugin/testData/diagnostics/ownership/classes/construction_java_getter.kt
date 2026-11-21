// FULL_JDK
// WITH_STDLIB

import java.io.File

open <!VERIFICATION_SKIPPED, VERIFICATION_SKIPPED!>class NamedFile<!> : File("x") {
    val label: () -> String = { name }
    val <!VIPER_TEXT!>lazyLabel<!>: String by <!OPAQUE_CALL!>lazy { name }<!>
}
