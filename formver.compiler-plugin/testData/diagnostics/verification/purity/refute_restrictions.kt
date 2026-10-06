// FULL_JDK
// DIAGNOSTIC_KINDS
import org.jetbrains.kotlin.formver.plugin.*

@NeverVerify
fun <!VERIFICATION_SKIPPED!>impurePredicate<!>() {
    var x = 0
    refute(<!PURITY_VIOLATION!>x++ > 0<!>)
}

class RefuteBox(var value: Int)

fun <!VERIFICATION_SKIPPED!>unownedRead<!>(box: RefuteBox) {
    refute(<!UNSUPPORTED_OWNERSHIP!>box.value<!> > 0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>ownedRead<!>(box: @Unique RefuteBox) {
    refute(box.value > 0)
}
