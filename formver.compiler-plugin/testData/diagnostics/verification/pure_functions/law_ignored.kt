// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface LawOnly {
    @Law @Pure @AlwaysVerify
    fun truth(): Boolean = true

    @Law @Pure @AlwaysVerify
    fun arithmetic(): Boolean {
        val n = 1 + 2
        return n > 0
    }
}

class LawImplementation : LawOnly

fun <!VIPER_TEXT!>unrelated<!>(): Boolean = true
