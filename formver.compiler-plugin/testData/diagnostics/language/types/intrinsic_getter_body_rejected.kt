// DIAGNOSTIC_KINDS

package org.jetbrains.kotlin.formver.plugin

<!VIPER_TEXT!>class Multiset<!><T> private constructor() {
    val size: Int
        <!VIPER_TEXT!>get<!>() {
            val count = 3
            verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>count == 4<!>)
            return count
        }
}
