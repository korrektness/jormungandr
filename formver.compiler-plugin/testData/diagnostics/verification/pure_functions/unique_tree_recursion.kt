// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Tree(val v: Int, var l: @Unique Tree?, var r: @Unique Tree?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>size<!>(t: @Unique Tree?): Int = if (t == null) 0 else 1 + size(t.l) + size(t.r)

@AlwaysVerify
fun <!VIPER_TEXT!>insert<!>(t: @Unique Tree?, v: Int): @Unique Tree {
    postconditions<Tree> { r -> size(r) == old(size(t)) + 1 }
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

@AlwaysVerify
fun <!VIPER_TEXT!>leaf<!>(v: Int): @Unique Tree {
    postconditions<Tree> { r -> size(r) == 1 }
    return Tree(v, null, null)
}

@AlwaysVerify
fun <!VIPER_TEXT!>rotateLeft<!>(t: @Unique Tree): @Unique Tree {
    postconditions<Tree> { r -> size(r) == old(size(t)) }
    val right: @Unique Tree? = t.r
    if (right == null) {
        t.r = right
        return t
    }
    verify(size(right) == 1 + size(right.l) + size(right.r))
    t.r = right.l
    verify(size(t) == 1 + size(t.l) + size(t.r))
    right.l = t
    return right
}
