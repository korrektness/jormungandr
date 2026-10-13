// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class LocalLaw<!>(var f: Int) {
    init { require(f > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = f > 0
}

fun <!VERIFICATION_SKIPPED!>viaCapture<!>(s: LocalLaw) {
    <!UNSUPPORTED_FEATURE, UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>local<!>() { s.f = 0 }<!>
    local()
}

fun <!VERIFICATION_SKIPPED!>viaParameter<!>(s: LocalLaw) {
    <!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>local<!>(t: LocalLaw) { <!UNSUPPORTED_FEATURE!>t.f = 0<!> }<!>
    local(s)
}
