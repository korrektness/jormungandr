// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>scanSkipLast<!>(offsets: @Unique @Borrowed IntArray, targets: @Unique @Borrowed IntArray, start: Int, parent: @Unique @Borrowed IntArray, rank: @Unique @Borrowed IntArray, queue: @Unique @Borrowed ArrayDeque<Int>, u: Int, processed: Int, found: Int): Int {
    preconditions {
        offsets.size >= 1
        offsets.size - 1 < 715827882
        parent.size == offsets.size - 1
        rank.size == offsets.size - 1
        0 <= start && start < offsets.size - 1
        forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (0 <= offsets[i] && offsets[i] <= targets.size) }
        forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (0 <= targets[j] && targets[j] < offsets.size - 1) }
        0 <= u && u < offsets.size - 1
        0 <= processed && processed < found
        rank[u] == processed
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && rank[v] == processed) implies (v == u) }
        rank[start] == 0
        1 <= found && found <= offsets.size - 1
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1) implies (-1 <= rank[v] && rank[v] < found) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0) implies (0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= rank[parent[v]] && rank[parent[v]] < rank[v]) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0 && 0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= offsets[parent[v]] && offsets[parent[v] + 1] <= targets.size) implies exists<Int> { j -> triggers(targets[j]); (offsets[parent[v]] <= j && j < offsets[parent[v] + 1]) && targets[j] == v } }
        queue.size == found - (processed + 1)
        forAll<Int> { q -> triggers(queue[q]); (0 <= q && q < queue.size) implies (0 <= queue[q] && queue[q] < offsets.size - 1) }
        forAll<Int> { q -> triggers(rank[queue[q]]); (0 <= q && q < queue.size && 0 <= queue[q] && queue[q] < offsets.size - 1) implies (rank[queue[q]] == (processed + 1) + q) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && (processed + 1) <= rank[v] && rank[v] < found) implies (queue[rank[v] - (processed + 1)] == v) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && 0 <= rank[v] && rank[v] < processed) implies forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[v] && offsets[v + 1] <= targets.size && offsets[v] <= j && j < offsets[v + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) } }
    }
    postconditions<Int> { r ->
        found <= r
        rank[start] == 0
        1 <= r && r <= offsets.size - 1
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1) implies (-1 <= rank[v] && rank[v] < r) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0) implies (0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= rank[parent[v]] && rank[parent[v]] < rank[v]) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0 && 0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= offsets[parent[v]] && offsets[parent[v] + 1] <= targets.size) implies exists<Int> { j -> triggers(targets[j]); (offsets[parent[v]] <= j && j < offsets[parent[v] + 1]) && targets[j] == v } }
        queue.size == r - (processed + 1)
        forAll<Int> { q -> triggers(queue[q]); (0 <= q && q < queue.size) implies (0 <= queue[q] && queue[q] < offsets.size - 1) }
        forAll<Int> { q -> triggers(rank[queue[q]]); (0 <= q && q < queue.size && 0 <= queue[q] && queue[q] < offsets.size - 1) implies (rank[queue[q]] == (processed + 1) + q) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && (processed + 1) <= rank[v] && rank[v] < r) implies (queue[rank[v] - (processed + 1)] == v) }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && 0 <= rank[v] && rank[v] < (processed + 1)) implies forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[v] && offsets[v + 1] <= targets.size && offsets[v] <= j && j < offsets[v + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) } }
        forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (offsets[i] == old(offsets[i])) }
        forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (targets[j] == old(targets[j])) }
    }
    var f = found
    var k = offsets[u]
    while (k < offsets[u + 1] - 1) {
        loopInvariants {
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (offsets[i] == old(offsets[i])) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (targets[j] == old(targets[j])) }
            offsets.size >= 1
            offsets.size - 1 < 715827882
            parent.size == offsets.size - 1
            rank.size == offsets.size - 1
            0 <= start && start < offsets.size - 1
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (0 <= offsets[i] && offsets[i] <= targets.size) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (0 <= targets[j] && targets[j] < offsets.size - 1) }
            0 <= u && u < offsets.size - 1
            0 <= processed && processed < f
            rank[u] == processed
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && rank[v] == processed) implies (v == u) }
            found <= f
            offsets[u] <= k
            forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[u] && offsets[u + 1] <= targets.size && offsets[u] <= j && j < k && j < offsets[u + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) }
            rank[start] == 0
            1 <= f && f <= offsets.size - 1
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1) implies (-1 <= rank[v] && rank[v] < f) }
            forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0) implies (0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= rank[parent[v]] && rank[parent[v]] < rank[v]) }
            forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0 && 0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= offsets[parent[v]] && offsets[parent[v] + 1] <= targets.size) implies exists<Int> { j -> triggers(targets[j]); (offsets[parent[v]] <= j && j < offsets[parent[v] + 1]) && targets[j] == v } }
            queue.size == f - (processed + 1)
            forAll<Int> { q -> triggers(queue[q]); (0 <= q && q < queue.size) implies (0 <= queue[q] && queue[q] < offsets.size - 1) }
            forAll<Int> { q -> triggers(rank[queue[q]]); (0 <= q && q < queue.size && 0 <= queue[q] && queue[q] < offsets.size - 1) implies (rank[queue[q]] == (processed + 1) + q) }
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && (processed + 1) <= rank[v] && rank[v] < f) implies (queue[rank[v] - (processed + 1)] == v) }
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && 0 <= rank[v] && rank[v] < processed) implies forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[v] && offsets[v + 1] <= targets.size && offsets[v] <= j && j < offsets[v + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) } }
        }
        val w = targets[k]
        if (rank[w] < 0) {
            check(f < rank.size)
            rank[w] = f
            parent[w] = u
            queue.addLast(w)
            f++
        }
        k++
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return f<!>
}

fun <!VIPER_TEXT!>bfsWithoutVertexBound<!>(offsets: @Unique @Borrowed IntArray, targets: @Unique @Borrowed IntArray, start: Int, parent: @Unique @Borrowed IntArray, rank: @Unique @Borrowed IntArray): Int {
    preconditions {
        offsets.size >= 1
        parent.size == offsets.size - 1
        rank.size == offsets.size - 1
        0 <= start && start < offsets.size - 1
        forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (0 <= offsets[i] && offsets[i] <= targets.size) }
        forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (0 <= targets[j] && targets[j] < offsets.size - 1) }
    }
    postconditions<Int> { r ->
        rank[start] == 0
        1 <= r && r <= offsets.size - 1
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1) implies (-1 <= rank[v] && rank[v] < r) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0) implies (0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= rank[parent[v]] && rank[parent[v]] < rank[v]) }
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0 && 0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= offsets[parent[v]] && offsets[parent[v] + 1] <= targets.size) implies exists<Int> { j -> triggers(targets[j]); (offsets[parent[v]] <= j && j < offsets[parent[v] + 1]) && targets[j] == v } }
        forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && 0 <= rank[v] && rank[v] < r) implies forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[v] && offsets[v + 1] <= targets.size && offsets[v] <= j && j < offsets[v + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) } }
        forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (offsets[i] == old(offsets[i])) }
        forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (targets[j] == old(targets[j])) }
    }
    var i = 0
    while (i < offsets.size - 1) {
        loopInvariants {
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (offsets[i] == old(offsets[i])) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (targets[j] == old(targets[j])) }
            offsets.size >= 1
                parent.size == offsets.size - 1
            rank.size == offsets.size - 1
            0 <= start && start < offsets.size - 1
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (0 <= offsets[i] && offsets[i] <= targets.size) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (0 <= targets[j] && targets[j] < offsets.size - 1) }
            0 <= i && i <= offsets.size - 1
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < i) implies (rank[v] == -1) }
        }
        rank[i] = -1
        i++
    }
    rank[start] = 0
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    queue.addLast(start)
    var processed = 0
    var found = 1
    while (!queue.isEmpty()) {
        loopInvariants {
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (offsets[i] == old(offsets[i])) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (targets[j] == old(targets[j])) }
            offsets.size >= 1
                parent.size == offsets.size - 1
            rank.size == offsets.size - 1
            0 <= start && start < offsets.size - 1
            forAll<Int> { i -> triggers(offsets[i]); (0 <= i && i < offsets.size) implies (0 <= offsets[i] && offsets[i] <= targets.size) }
            forAll<Int> { j -> triggers(targets[j]); (0 <= j && j < targets.size) implies (0 <= targets[j] && targets[j] < offsets.size - 1) }
            0 <= processed && processed <= found
            rank[start] == 0
            1 <= found && found <= offsets.size - 1
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1) implies (-1 <= rank[v] && rank[v] < found) }
            forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0) implies (0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= rank[parent[v]] && rank[parent[v]] < rank[v]) }
            forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < offsets.size - 1 && rank[v] > 0 && 0 <= parent[v] && parent[v] < offsets.size - 1 && 0 <= offsets[parent[v]] && offsets[parent[v] + 1] <= targets.size) implies exists<Int> { j -> triggers(targets[j]); (offsets[parent[v]] <= j && j < offsets[parent[v] + 1]) && targets[j] == v } }
            queue.size == found - processed
            forAll<Int> { q -> triggers(queue[q]); (0 <= q && q < queue.size) implies (0 <= queue[q] && queue[q] < offsets.size - 1) }
            forAll<Int> { q -> triggers(rank[queue[q]]); (0 <= q && q < queue.size && 0 <= queue[q] && queue[q] < offsets.size - 1) implies (rank[queue[q]] == processed + q) }
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && processed <= rank[v] && rank[v] < found) implies (queue[rank[v] - processed] == v) }
            forAll<Int> { v -> triggers(rank[v]); (0 <= v && v < offsets.size - 1 && 0 <= rank[v] && rank[v] < processed) implies forAll<Int> { j -> triggers(targets[j]); (0 <= offsets[v] && offsets[v + 1] <= targets.size && offsets[v] <= j && j < offsets[v + 1] && 0 <= targets[j] && targets[j] < offsets.size - 1) implies (0 <= rank[targets[j]]) } }
        }
        val u = queue.removeFirst()
        found = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>scanSkipLast(offsets, targets, start, parent, rank, queue, u, processed, found)<!>
        processed++
    }
    return found
}

fun <!VIPER_TEXT!>parentCycleWithoutRanks<!>(parent: @Unique @Borrowed IntArray, rank: @Unique @Borrowed IntArray, a: Int, b: Int) {
    preconditions {
        parent.size == rank.size
        0 <= a && a < parent.size
        0 <= b && b < parent.size
        rank[a] > 0
        rank[b] > 0
        forAll<Int> { v -> triggers(parent[v]); (0 <= v && v < parent.size && rank[v] > 0) implies (0 <= parent[v] && parent[v] < rank.size) }
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>!(parent[a] == b && parent[b] == a)<!>)
}
