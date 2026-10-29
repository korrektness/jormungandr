// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Tree<!>(val v: Int, var l: @Unique Tree?, var r: @Unique Tree?)

// A tree has no bound on its node count, so the additions in `size` are overflow-negative tests and callers see no
// definition of `size`.
@Pure
fun <!VIPER_TEXT!>size<!>(t: @Unique Tree?): Int {
    preconditions { decreases(t) }
    return if (t == null) 0 else <!INTEGER_OVERFLOW!><!INTEGER_OVERFLOW!>1 + size(t.l)<!> + size(t.r)<!>
}

// `capSize` saturates, so `cappedSize` stays within Int on any tree.
@Pure
fun <!VIPER_TEXT!>capSize<!>(k: Int): Int = if (k > 1000000) 1000000 else k

@Pure
fun <!VIPER_TEXT!>cappedSize<!>(t: @Unique Tree?): Int {
    preconditions { decreases(t) }
    postconditions<Int> { r -> 0 <= r && r <= 1000000 }
    return if (t == null) 0 else capSize(1 + cappedSize(t.l) + cappedSize(t.r))
}

fun <!VIPER_TEXT!>insert<!>(t: @Unique Tree?, v: Int): @Unique Tree {
    postconditions<Tree> { r -> cappedSize(r) == capSize(old(cappedSize(t)) + 1) }
    if (t == null) {
        return Tree(v, null, null)
    }
    if (v < t.v) {
        t.l = insert(t.l, v)
    } else {
        t.r = insert(t.r, v)
    }
    return t
}

@Pure
fun <!VIPER_TEXT!>leafSize<!>(t: @Unique Tree?): Int {
    preconditions { t == null || (t.l == null && t.r == null) }
    return if (t == null) 0 else 1
}

fun <!VIPER_TEXT!>leaf<!>(v: Int): @Unique Tree {
    postconditions<Tree> { r -> r.l == null && r.r == null; leafSize(r) == 1 }
    val tree: @Unique Tree = Tree(v, null, null)
    tree.l = null
    tree.r = null
    return tree
}

fun <!VIPER_TEXT!>rotateLeft<!>(t: @Unique Tree): @Unique Tree {
    postconditions<Tree> { r -> cappedSize(r) == old(cappedSize(t)) }
    val right: @Unique Tree? = t.r
    if (right == null) {
        t.r = right
        return t
    }
    verify(cappedSize(right) == capSize(1 + cappedSize(right.l) + cappedSize(right.r)))
    t.r = right.l
    verify(cappedSize(t) == capSize(1 + cappedSize(t.l) + cappedSize(t.r)))
    right.l = t
    return right
}
