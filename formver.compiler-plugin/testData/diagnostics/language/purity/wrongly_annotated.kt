// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.Pure

fun <!VIPER_TEXT!>iAmAMethod<!>(): Int {
    return 1
}

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>testWronglyAnnotatedAsPure<!>(): Int {
    return iAmAMethod()
}<!>

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>testExecutingLambda<!>(action: () -> Int): Int {
    return action()
}<!>

<!VIPER_TEXT!>class Field<!>(var value: Int)

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>testFieldModification<!>(field: Field): Int {
    <!UNSUPPORTED_OWNERSHIP!>field.value<!> += 1
    return <!UNSUPPORTED_OWNERSHIP!>field.value<!>
}<!>

fun Field.<!VIPER_TEXT!>impureExtension<!>() {
    val before = value
    if (before == 2147483647) throw IllegalArgumentException()
    this.value = before + 1
}

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>testImpureExtensionCall<!>(field: Field): Int {
    field.impureExtension()
    return <!UNSUPPORTED_OWNERSHIP!>field.value<!>
}<!>

<!PURITY_VIOLATION!>@Pure
fun Field.<!VERIFICATION_SKIPPED!>wronglyAnnotatedExtension<!>() {
    <!UNSUPPORTED_OWNERSHIP!>this.value<!> += 1
}<!>

<!VIPER_TEXT!>class Wrapper<!>(val field: Field)

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>testNestedFieldModification<!>(wrapper: Wrapper): Int {
    <!UNSUPPORTED_OWNERSHIP!>wrapper.field.value<!> += 1
    return <!UNSUPPORTED_OWNERSHIP!>wrapper.field.value<!>
}<!>
