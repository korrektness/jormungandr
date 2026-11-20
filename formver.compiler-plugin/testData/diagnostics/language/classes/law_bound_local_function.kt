// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class LocalLaw<!>(var f: Int) {
    init { require(f > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = f > 0
}

fun <!VIPER_TEXT!>viaCapture<!>(s: LocalLaw) {
    fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>local<!>() { <!UNSUPPORTED_OWNERSHIP!>s.f = 0<!> }
    local()
}

fun <!VIPER_TEXT!>viaParameter<!>(s: LocalLaw) {
    fun <!VERIFICATION_SKIPPED!>local<!>(t: LocalLaw) { <!UNSUPPORTED_FEATURE!>t.f = 0<!> }
    local(s)
}
