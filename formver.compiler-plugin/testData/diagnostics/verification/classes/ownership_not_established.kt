// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Leaf(var n: Int)

class Holder(val leaf: @Unique Leaf, var count: Int)

fun <!VIPER_TEXT!>bump<!>(l: @Unique @Borrowed Leaf) {
    l.n = l.n + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>borrowThroughVal<!>(h: @Unique @Borrowed Holder) {
    <!OWNERSHIP_NOT_ESTABLISHED!>bump(h.leaf)<!>
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>writeThroughVal<!>(h: @Unique Holder) {
    <!UNSUPPORTED_OWNERSHIP!>h.leaf.n = 3<!>
}

<!UNSUPPORTED_OWNERSHIP!>@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>returnThroughVal<!>(h: @Unique Holder): @Unique Leaf {
    return h.leaf
}<!>
