// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalCounterSpecs.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/IntAbs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*
import kotlin.math.abs

@NeverVerify
fun <!VIPER_TEXT!>trustedSuccessor<!>(x: Int): Int {
    preconditions { x < Int.MAX_VALUE }
    postconditions<Int> { it > x }
    return x + 1
}

@NeverConvert
class Opaque {
    fun answer(): Int = 42
}

fun <!VIPER_TEXT!>selfContained<!>(x: Int): Int {
    postconditions<Int> { it == x }
    return x
}

fun <!VIPER_TEXT!>usesSelfContained<!>(): Int = selfContained(3)

fun <!VIPER_TEXT!>usesNeverVerify<!>() {
    val next = trustedSuccessor(1)
    verify(next > 1)
}

fun <!VIPER_TEXT!>usesNeverConvert<!>(opaque: Opaque): Int = opaque.answer()

fun <!VIPER_TEXT!>usesUserSpec<!>(counter: @Unique @Borrowed Counter) {
    verify(counter.value() >= 0)
    verify(twice(4) == 8)
}

fun <!VIPER_TEXT!>usesBundledSpec<!>() {
    verify(abs(-3) == 3)
}

fun <!VIPER_TEXT!>usesBundledListSpec<!>(list: @Unique @Borrowed List<Int>): Int = if (list.isEmpty()) 0 else list[0]

fun <!VIPER_TEXT!>usesLibraryPropertyContract<!>(text: String) {
    val last = text.lastIndex
    verify(last == text.length - 1)
}

fun <!VIPER_TEXT!>usesSeveral<!>(counter: @Unique @Borrowed Counter, list: @Unique @Borrowed List<Int>) {
    val first = if (list.isEmpty()) 0 else list[0]
    val next = trustedSuccessor(abs(1))
    verify(next > 1)
    verify(counter.value() >= 0)
}
