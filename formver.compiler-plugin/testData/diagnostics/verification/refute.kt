// FULL_JDK
// DIAGNOSTIC_KINDS
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>reachable<!>() {
    refute(false)
}

fun <!VIPER_TEXT!>inconsistentPreconditions<!>(x: Int) {
    preconditions { x > 0; x < 0 }
    refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

fun <!VIPER_TEXT!>inconsistentLoopAssumptions<!>() {
    var i = 0
    while (i < 0) {
        loopInvariants { i >= 0 }
        refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
        i++
    }
}

fun <!VIPER_TEXT!>unknownPredicate<!>(x: Int) {
    refute(x > 0)
}

fun <!VIPER_TEXT!>knownPredicate<!>(x: Int) {
    preconditions { x > 0 }
    refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x > 0<!>)
}

fun <!VIPER_TEXT!>multipleProbes<!>(x: Int) {
    refute(false)
    refute(x > 0)
    refute(x <= 0)
    verify(true)
}

fun <!VIPER_TEXT!>mixedProbes<!>() {
    refute(false)
    refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>true<!>)
    refute(false)
}

fun <!VIPER_TEXT!>assertionAfterRefutation<!>() {
    refute(false)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

fun <!VIPER_TEXT!>specificationPredicate<!>() {
    refute(multisetOf(1).count(1) == 0)
}

// A probe after a join is reachable through the live branch, so it does not show that the dead branch is vacuous.
fun <!VIPER_TEXT!>branchHidden<!>(x: Int) {
    if (x > 0 && x < 0) {
        verify(false)
    }
    refute(false)
}

fun <!VIPER_TEXT!>branchProbed<!>(x: Int) {
    if (x > 0 && x < 0) {
        refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    }
}
