// DIAGNOSTIC_KINDS

package org.jetbrains.kotlin.formver.plugin

<!VIPER_TEXT!>class Multiset<!><T> private constructor() {
    private var count = 3

    init {
        verify(count == 3)
    }
}

<!VIPER_TEXT!>class IterationSequence<!><out T> private constructor() {
    private var count = 3

    init {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>count == 4<!>)
    }
}
