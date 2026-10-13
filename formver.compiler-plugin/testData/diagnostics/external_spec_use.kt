// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalCounterSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*

@Pure
fun <!VIPER_TEXT!>readLibraryValue<!>(counter: @Unique @Borrowed Counter): Int = counter.value()

fun <!VIPER_TEXT!>lawOnLibraryReceiver<!>(counter: @Unique @Borrowed Counter) {
    verify(counter.value() >= 0)
}

fun <!VIPER_TEXT!>factoryAndMutation<!>() {
    val counter = create(2)
    verify(counter.value() == 2)
    verify(counter.label == 2)
    val result = counter.advance(3)
    verify(result == 5)
    verify(counter.value() == 5)
    val shifted = counter.shift(4)
    verify(shifted == 9)
    verify(counter.value() == 9)
    verify(counter.label == 9)
}

fun <!VIPER_TEXT!>renamedOverload<!>(counter: @Unique @Borrowed Counter) {
    val capped = counter.bounded(7)
    verify(capped <= 7)
}

fun <!VIPER_TEXT!>topLevelAndGeneric<!>() {
    verify(twice(4) == 8)
    verify(identity(13) == 13)
}

fun <!VIPER_TEXT!>genericMember<!>(cell: @Unique @Borrowed Cell<Int>) {
    cell.set(13)
    verify(cell.get() == 13)
}

fun <!VIPER_TEXT!>incorrectClaim<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>twice(4) == 9<!>)
}

fun <!VIPER_TEXT!>unmetPrecondition<!>() {
    val counter: @Unique Counter = create(2)
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>counter.advance(-1)<!>
}
