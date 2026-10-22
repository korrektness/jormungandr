// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

// order and rank are inverse permutations of 0 until n; the swap moves w to position f. The bounds quantifiers
// trigger on one read; the inverse quantifiers trigger on the nested read, which keeps instantiation finite.
fun <!VIPER_TEXT!>permutationSwap<!>(order: @Unique @Borrowed IntArray, rank: @Unique @Borrowed IntArray, f: Int, w: Int) {
    preconditions {
        order.size == rank.size
        0 <= f && f < order.size
        0 <= w && w < order.size
        f <= rank[w]
        forAll<Int> { k -> triggers(order[k]); (0 <= k && k < order.size) implies (0 <= order[k] && order[k] < rank.size) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < rank.size) implies (0 <= rank[v] && rank[v] < order.size) }
        forAll<Int> { k -> triggers(rank[order[k]]); (0 <= k && k < order.size) implies (rank[order[k]] == k) }
        forAll<Int> { v -> triggers(order[rank[v]]); (0 <= v && v < rank.size) implies (order[rank[v]] == v) }
    }
    postconditions<Unit> {
        rank[w] == f && order[f] == w
    }
    val x = order[f]
    val r = rank[w]
    order[f] = w
    order[r] = x
    rank[w] = f
    rank[x] = r
}

fun <!VIPER_TEXT!>permutationSwapWrong<!>(order: @Unique @Borrowed IntArray, rank: @Unique @Borrowed IntArray, f: Int, w: Int) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions {
        order.size == rank.size
        0 <= f && f < order.size
        0 <= w && w < order.size
        f <= rank[w]
        forAll<Int> { k -> triggers(order[k]); (0 <= k && k < order.size) implies (0 <= order[k] && order[k] < rank.size) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < rank.size) implies (0 <= rank[v] && rank[v] < order.size) }
        forAll<Int> { k -> triggers(rank[order[k]]); (0 <= k && k < order.size) implies (rank[order[k]] == k) }
        forAll<Int> { v -> triggers(order[rank[v]]); (0 <= v && v < rank.size) implies (order[rank[v]] == v) }
    }
    postconditions<Unit> {
        rank[w] == f && order[f] == f
    }
    val x = order[f]
    val r = rank[w]
    order[f] = w
    order[r] = x
    rank[w] = f
    rank[x] = r
}<!>

// A trigger on a read of a unique array under a conjunction body.
fun <!VIPER_TEXT!>triggeredBound<!>(a: @Unique @Borrowed IntArray, b: @Unique @Borrowed IntArray, u: Int) {
    preconditions {
        0 <= u && u < a.size - 1
        forAll<Int> { i -> triggers(a[i]); (0 <= i && i < a.size) implies (0 <= a[i] && a[i] <= b.size) }
    }
    verify(a[u + 1] - a[u] >= -b.size)
}

fun <!VIPER_TEXT!>triggeredBoundWrong<!>(a: @Unique @Borrowed IntArray, b: @Unique @Borrowed IntArray, u: Int) {
    preconditions {
        0 <= u && u < a.size - 1
        forAll<Int> { i -> triggers(a[i]); (0 <= i && i < a.size) implies (0 <= a[i] && a[i] <= b.size) }
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a[u + 1] - a[u] >= 0<!>)
}
