// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Config<!>(val a: Boolean) {
    companion <!VIPER_TEXT!>object<!> {
        const val LIMIT = 4
        val DEFAULT: Boolean = true

        fun <!VIPER_TEXT!>make<!>(): Config {
            postconditions<Config> { it.a }
            return Config(true)
        }
    }

    fun <!VIPER_TEXT!>again<!>(): Config {
        postconditions<Config> { it.a }
        return make()
    }
}

<!VIPER_TEXT!>object Registry<!> {
    val size: Int = 3

    fun <!VIPER_TEXT!>same<!>(n: Int): Int {
        postconditions<Int> { it == n }
        return n
    }

    fun <!VIPER_TEXT!>ownSize<!>(): Int = size
}

<!VIPER_TEXT!>class Outer<!> {
    <!VIPER_TEXT!>class Inner<!>(val v: Int)
}

fun <!VIPER_TEXT!>useCompanion<!>() {
    val c = Config.make()
    verify(c.a)
}

fun <!VIPER_TEXT!>readConst<!>() {
    verify(Config.LIMIT == 4)
}

fun <!VIPER_TEXT!>readCompanionProperty<!>(): Boolean = Config.DEFAULT

fun <!VIPER_TEXT!>companionPropertyIsOpaque<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Config.DEFAULT<!>)
}

fun <!VIPER_TEXT!>useObject<!>() {
    val n = Registry.same(2)
    verify(n == 2)
}

fun <!VIPER_TEXT!>objectPropertyIsOpaque<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Registry.size == 3<!>)
}

fun <!VIPER_TEXT!>nested<!>(i: Outer.Inner): Int = i.v
