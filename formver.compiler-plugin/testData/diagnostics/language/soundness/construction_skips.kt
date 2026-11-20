// FULL_JDK
// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

fun interface Acc {
    @GhostObserver @Pure fun <!VIPER_TEXT!>total<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @SpecOf("invoke") fun invokeSpec(amount: Int) {
        preconditions { 0 <= amount && amount <= 1000 && 0 <= total() && total() <= 1000 }
        postconditions<Int> { r -> total() == old(total()) + amount && r == total() }
    }
}

// A delegated property skips its class's construction; clients get no field facts.
<!VERIFICATION_SKIPPED, VIPER_TEXT!>class LazyHolder<!>(val n: Int) {
    val <!VIPER_TEXT!>action<!>: Acc by <!OPAQUE_CALL!>lazy {
        Acc { amount ->
            exposes(Acc::class) { total() == 0 }
            <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>-9<!>
        }
    }<!>
}

fun <!VIPER_TEXT!>lazyHolderClient<!>() {
    val h = LazyHolder(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>h.n == 1<!>)
}

<!VERIFICATION_SKIPPED!>class LazyValue<!>(val n: Int) {
    val <!VIPER_TEXT!>doubled<!>: Int by <!OPAQUE_CALL!>lazy { 2 * n }<!>
}

fun <!VIPER_TEXT!>lazyValueClient<!>() {
    val v = LazyValue(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v.n == 1<!>)
}

// A property left unassigned by the initialization code skips the construction.
<!VERIFICATION_SKIPPED!>class Late<!>(val a: Int) {
    lateinit var s: String
}

fun <!VIPER_TEXT!>lateClient<!>() {
    val l = Late(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>l.a == 1<!>)
}
