// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Leaf(var n: Int)

class Holder(val leaf: @Unique Leaf, var count: Int)

fun <!VIPER_TEXT!>bump<!>(l: @Unique @Borrowed Leaf) {
    l.n = l.n + 1
}

fun <!VIPER_TEXT!>makeHolder<!>(): @Unique Holder = Holder(Leaf(0), 0)

@AlwaysVerify
fun <!VIPER_TEXT!>borrowThroughCallResult<!>() {
    <!OWNERSHIP_NOT_ESTABLISHED!>bump(makeHolder().leaf)<!>
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>writeThroughCallResult<!>() {
    <!UNSUPPORTED_OWNERSHIP!>makeHolder().leaf.n = 3<!>
}
