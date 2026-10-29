// FULL_JDK
// FULL_FILE_VERIFICATION

import org.jetbrains.kotlin.formver.plugin.*

// The inconsistent pure context belongs only to its callers.
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Pure @NeverVerify
fun <!VIPER_TEXT!>contradictory<!>(): Boolean {
    postconditions<Boolean> { result -> result && !result }
    return true
}<!>

fun <!VIPER_TEXT!>usesContradiction<!>() {
    val impossible = contradictory()
    verify(impossible && !impossible)
}

fun <!VIPER_TEXT!>isolatedSibling<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

@Suppress("VERIFICATION_CONDITION_NOT_ESTABLISHED")
fun <!VIPER_TEXT!>suppressedSibling<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 0<!>)
}

fun <!VERIFICATION_SKIPPED!>localOwner<!>() {
    <!UNSUPPORTED_FEATURE!>fun <!VIPER_TEXT!>local<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>) }<!>
    local()
}

<!VIPER_TEXT!>class AccessorOwner<!> {
    var value: Int = 0
        <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); return field }
        <!VIPER_TEXT!>set<!>(v) { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); field = v }
}

fun interface BatchAction {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke")
    fun invokeSpec() { postconditions<Int> { result -> result == 1 } }
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

fun <!VIPER_TEXT!>lambdaOwner<!>(): @Unique BatchAction = BatchAction {
    exposes(BatchAction::class) { state() == 0 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    1
}

<!VIPER_TEXT!>class ClassOwner<!> {
    val action: @Unique BatchAction = BatchAction {
        exposes(BatchAction::class) { state() == 0 }
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
        1
    }
}

@Suppress("VERIFICATION_CONDITION_NOT_ESTABLISHED")
<!VIPER_TEXT!>class SuppressedOwner<!> {
    val value: Int
        <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 0<!>); return 0 }
    val action: @Unique BatchAction = BatchAction {
        exposes(BatchAction::class) { state() == 0 }
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 0<!>)
        1
    }
}

val <!VIPER_TEXT!>propertyOwner<!>: @Unique BatchAction = BatchAction {
    exposes(BatchAction::class) { state() == 0 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    1
}
