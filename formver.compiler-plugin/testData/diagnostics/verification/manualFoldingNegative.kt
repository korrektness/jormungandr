// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
class Cell(
    var value: @Unique Int
)

// Writing a field of a @Manual class without unfolding its predicate must fail:
// the permission to the field is still held inside the folded predicate.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>writeWithoutUnfold<!>(c: @Unique Cell) {
    c.value = <!UNIQUENESS_MISMATCH!>5<!>
}

// Unfolding the predicate and returning without folding it back must fail:
// the borrowed parameter's predicate is not re-established for the caller.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>unfoldWithoutRefold<!>(c: @Unique @Borrowed Cell) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    unfold(UniquePred(<!LOCALITY_MISMATCH!>c<!>))
}<!>
