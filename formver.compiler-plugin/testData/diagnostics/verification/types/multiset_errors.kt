import org.jetbrains.kotlin.formver.plugin.*

class Box(val value: Int)

<!UNSUPPORTED_FEATURE!>@Pure
fun boxes(b: Box): Multiset<Box> = multisetOf(b)<!>

fun <!VERIFICATION_SKIPPED!>multisetInBody<!>(x: Int): Int {
    val m = <!UNSUPPORTED_FEATURE!>multisetOf(x)<!>
    return x
}

class Node(val value: Int, var next: @Unique Node?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>contents<!>(n: @Unique Node?): Multiset<Int> =
    if (n == null) multisetOf() else multisetOf(n.value) + contents(n.next)

fun <!VERIFICATION_SKIPPED!>handBack<!>(n: @Unique Node): @Unique Node {
    postconditions<Node> { r -> contents(r) == contents(<!UNSUPPORTED_OWNERSHIP!>n<!>) }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>handBackOld<!>(n: @Unique Node): @Unique Node {
    postconditions<Node> { r -> contents(r) == old(contents(n)) }
    return n
}
