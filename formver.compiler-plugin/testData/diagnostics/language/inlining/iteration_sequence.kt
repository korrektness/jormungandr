// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>integerSequence<!>() {
    verify(iterationSequenceOf(3, 7).size == 2)
    verify(iterationSequenceOf(3, 7)[0] == 3)
    verify(iterationSequenceOf(3, 7)[1] == 7)
    verify(7 in iterationSequenceOf(3, 7))
    verify(9 !in iterationSequenceOf(3, 7))
    verify(iterationSequenceOf(3, 7).take(1) == iterationSequenceOf(3))
    verify(iterationSequenceOf<Int>().size == 0)
}

fun <!VIPER_TEXT!>referenceSequence<!>(first: String, second: String) {
    verify(iterationSequenceOf(first, second)[0] == first)
    verify(iterationSequenceOf(first, second).take(0) == iterationSequenceOf<String>())
    verify(iterationSequenceOf<String?>(null, first)[0] == null)
    verify(first in iterationSequenceOf(first, second))
    verify("absent" !in iterationSequenceOf("present"))
    verify(null in iterationSequenceOf<String?>(null, first))
    verify(first in iterationSequenceOf<String?>(null, first))
    verify("absent" !in iterationSequenceOf<String?>(null, "present"))
    verify(null !in iterationSequenceOf<String?>("present"))
    verify(7 in iterationSequenceOf<Int?>(null, 7))
    verify(9 !in iterationSequenceOf<Int?>(null, 7))
    verify(null !in iterationSequenceOf<Int?>(7))
}

fun <!VIPER_TEXT!>wrongReferenceMembership<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>"absent" in iterationSequenceOf("present")<!>)
}

<!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>runtimeSequence<!>() {
    iterationSequenceOf(3, 7)
}<!>

fun <!VIPER_TEXT!>sequenceBounds<!>() {
    verify(<!VIPER_VERIFICATION_ERROR!>iterationSequenceOf(3)[1]<!> == 3)
}

fun <!VIPER_TEXT!>sequencePrefixBounds<!>() {
    verify(iterationSequenceOf(3).take(2) == iterationSequenceOf(3))
    verify(iterationSequenceOf(3).take(-1) == iterationSequenceOf<Int>())
}

fun <!VIPER_TEXT!>wrongSequenceElement<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>iterationSequenceOf(3, 7)[0] == 7<!>)
}
