package org.jetbrains.kotlin.formver.plugin

<!VIPER_TEXT!>class Multiset<!><T> private constructor() {
    val size: Int
        <!VIPER_TEXT!>get<!>() {
            val count = 3
            verify(count == 3)
            return count
        }
}
