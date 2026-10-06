// FULL_JDK
// DIAGNOSTIC_KINDS
import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>reachable<!>() {
    refute(false)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inconsistentPreconditions<!>(x: Int) {
    preconditions { x > 0; x < 0 }
    refute(<!VIPER_VERIFICATION_ERROR!>false<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inconsistentLoopAssumptions<!>() {
    var i = 0
    while (i < 0) {
        loopInvariants { i >= 0 }
        refute(<!VIPER_VERIFICATION_ERROR!>false<!>)
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>unknownPredicate<!>(x: Int) {
    refute(x > 0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>knownPredicate<!>(x: Int) {
    preconditions { x > 0 }
    refute(<!VIPER_VERIFICATION_ERROR!>x > 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>multipleProbes<!>(x: Int) {
    refute(false)
    refute(x > 0)
    refute(x <= 0)
    verify(true)
}

@AlwaysVerify
fun <!VIPER_TEXT!>mixedProbes<!>() {
    refute(false)
    refute(<!VIPER_VERIFICATION_ERROR!>true<!>)
    refute(false)
}

@AlwaysVerify
fun <!VIPER_TEXT!>assertionAfterRefutation<!>() {
    refute(false)
    verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>specificationPredicate<!>() {
    refute(multisetOf(1).count(1) == 0)
}
