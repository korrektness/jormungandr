// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class SetterLaw<!>(f0: Int) {
    var f: Int = f0
        <!VERIFICATION_SKIPPED!>set<!>(value) { <!UNSUPPORTED_FEATURE!>field = value<!> }
    init { require(f > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = <!INVALID_LAW!>f<!> > 0
}

fun <!VERIFICATION_SKIPPED!>viaSetter<!>(s: SetterLaw) { <!UNSUPPORTED_FEATURE!>s.f = 0<!> }

open <!VIPER_TEXT!>class OpenLaw<!>(open var f: Int) {
    @Law @Pure @Unique fun invariant(): Boolean = <!INVALID_LAW!>f<!> > 0
}

fun <!VERIFICATION_SKIPPED!>viaOpen<!>(s: OpenLaw) { <!UNSUPPORTED_FEATURE!>s.f = 0<!> }
