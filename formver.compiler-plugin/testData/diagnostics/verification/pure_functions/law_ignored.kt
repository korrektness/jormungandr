// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface LawOnly {
    @Law @Pure
    fun truth(): Boolean = true

    @Law @Pure
    fun arithmetic(): Boolean {
        val n = 1 + 2
        return n > 0
    }
}

<!VIPER_TEXT!>class LawImplementation<!> : LawOnly

fun <!VIPER_TEXT!>unrelated<!>(): Boolean = true
