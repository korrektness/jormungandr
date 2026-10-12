// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class PhaseTwoPassing<!> {
    val value = 1
    init { observe(this) }
    @Law @Pure fun invariant(): Boolean = value > 0
}

fun <!VIPER_TEXT!>observe<!>(value: @Unique @Borrowed PhaseTwoPassing) {
    require(value.value > 0)
}

<!VERIFICATION_SKIPPED!>class LawFreeFallback<!> {
    val stored = 1
    val early = stored
    val later = 2
}

fun <!VIPER_TEXT!>fallbackFacts<!>() {
    val value: @Unique LawFreeFallback = LawFreeFallback()
    require(value.stored == 1)
    require(value.later == 2)
}
