// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Getter {
    fun <!VIPER_TEXT!>value<!>(): Int
}

open <!VIPER_TEXT!>class Base<!>(val n: Int)

<!VIPER_TEXT!>class Outer<!>(n: Int) : Base(n) {
    fun <!VIPER_TEXT!>get<!>(): Int = 1

    inner <!VIPER_TEXT!>class In<!>(n: Int) : Base(n) {
        val outerN = this@Outer.n

        <!UNSUPPORTED_FEATURE!>@Pure fun <!VERIFICATION_SKIPPED!>outerPure<!>(): Int = this@Outer.n<!>

        <!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>outerContract<!>(): Int {
            preconditions { this@Outer.n > 0 }
            return 0
        }<!>

        fun <!VIPER_TEXT!>labelled<!>(): Int = this@Outer.get()

        fun <!VIPER_TEXT!>implicit<!>(): Int = get()

        fun <!VIPER_TEXT!>innerState<!>(): Int {
            preconditions { n > 5 }
            verify(this@In.n > 5)
            return n
        }

        fun <!VIPER_TEXT!>outerIsNotInner<!>(): Int {
            preconditions { n > 5 }
            verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>this@Outer.n > 5<!>)
            return this@Outer.n
        }

        fun <!VIPER_TEXT!>outerIsOneObject<!>(): Int {
            val first = this@Outer.n
            verify(first == this@Outer.n)
            return first
        }

        inner <!VIPER_TEXT!>class Deeper<!> {
            fun <!VIPER_TEXT!>twoLevels<!>(): Int {
                verify(this@In.n == this@In.n && this@Outer.n == this@Outer.n)
                return get()
            }
        }
    }

    fun <!VERIFICATION_SKIPPED!>anonymous<!>(): Getter = <!UNSUPPORTED_FEATURE!>object : Getter<!> {
        override fun <!VIPER_TEXT!>value<!>(): Int {
            verify(this@Outer.n == this@Outer.n)
            return get()
        }
    }
}
