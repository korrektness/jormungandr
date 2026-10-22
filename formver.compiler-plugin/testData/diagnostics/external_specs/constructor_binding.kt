// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalConstructorSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt
import org.jetbrains.kotlin.formver.plugin.*
import external.library.*

fun <!VIPER_TEXT!>constructCounter<!>() {
    val c: @Unique Counter = Counter(2)
    verify(c.value() == 2)
    c.advance(1)
    verify(c.value() == 3)
}

fun <!VIPER_TEXT!>wrongCounter<!>() {
    val c: @Unique Counter = Counter(2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.value() == 3<!>)
}

fun <!VIPER_TEXT!>invalidInitial<!>() {
    val c: @Unique Counter = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Counter(-1)<!>
}

fun <!VIPER_TEXT!>constructDeque<!>() {
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    verify(queue.size == 0)
}

fun <!VIPER_TEXT!>listPresentation<!>(list: @Unique @Borrowed List<Int>) {
    verify(list.size >= 0)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>invalidPresentation<!>(queue: @Unique @Borrowed ArrayDeque<Int>) {
    <!UNSUPPORTED_OWNERSHIP!>listPresentation(queue)<!>
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>invalidExtensionPresentation<!>(queue: @Unique @Borrowed ArrayDeque<Int>) {
    val index = <!UNSUPPORTED_OWNERSHIP!>queue.lastIndex<!>
}

fun <!VIPER_TEXT!>constructCell<!>() {
    val cell: @Unique Cell<Int> = Cell(13)
    verify(cell.get() == 13)
    cell.set(14)
    verify(cell.get() == 14)
}
