// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// A read of a property that is not a field gives a fresh value of its type (docs/soundness.md, L9).

<!VIPER_TEXT!>class Box<!>(var n: Int)

val Box.poke: Int
    <!VIPER_TEXT!>get<!>() {
        n = 5
        return 0
    }

// A getter cannot reach owned state: passing an owned receiver to it is rejected.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>extensionGetterEffect<!>(c: @Unique @Borrowed Box) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    c.n = 1
    val ignored = <!LOCALITY_MISMATCH!>c<!>.poke
    verify(<!INVALID_MOVED_ACCESS!>c<!>.n == 1)
}<!>

var counter: Int = 0

// Two reads of a top-level property need not agree.
fun <!VIPER_TEXT!>topLevelReadsDiffer<!>() {
    val a = counter
    val b = counter
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == b<!>)
}
