// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalSharedConstructorSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt
import org.jetbrains.kotlin.formver.plugin.*
import external.library.*

fun <!VIPER_TEXT!>sharedConstructor<!>() {
    val cell = Cell(13)
    <!UNCHECKED_LIBRARY_CALL!>cell.set(14)<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!UNCHECKED_LIBRARY_CALL!>cell.get()<!> == 14<!>)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>ownedConstructor<!>(): @Unique Cell<Int> = <!UNIQUENESS_MISMATCH!>Cell(13)<!>
