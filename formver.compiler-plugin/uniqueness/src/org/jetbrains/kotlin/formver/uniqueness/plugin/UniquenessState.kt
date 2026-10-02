package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol

/** The uniqueness of every path below a root, keyed by path components of type [Key]. */
typealias UniquenessTrie<Key> = PathTrie<Key, Uniqueness>

typealias UniquenessState = UniquenessTrie<FirBasedSymbol<*>>

val EmptyUniquenessState = UniquenessState(Uniqueness.Unique)

/**
 * Performs the join of two uniqueness tries.
 */
fun <Key> UniquenessTrie<Key>.join(other: UniquenessTrie<Key>): UniquenessTrie<Key> =
    join(other, UniquenessUnifier)

/**
 * Enumerates the paths whose uniqueness state is [Uniqueness.Moved], including the empty path when [includeRoot] is set
 * and the root itself has moved.
 */
fun <Key> UniquenessTrie<Key>.enumerateInconsistentPaths(includeRoot: Boolean = false): Sequence<List<Key>> {
    val childPaths = enumerate(emptyList()) { data == Uniqueness.Moved }

    return if (includeRoot && data == Uniqueness.Moved) {
        sequenceOf(emptyList<Key>()) + childPaths
    } else {
        childPaths
    }
}

/**
 * Replaces the substate at [path] with [child].
 */
fun <Key> UniquenessTrie<Key>.insert(path: List<Key>, child: UniquenessTrie<Key>): UniquenessTrie<Key> =
    if (path.isEmpty()) {
        child
    } else {
        val head = path.first()
        copy(
            children = children.put(head, (children[head] ?: UniquenessTrie(Uniqueness.Unique)).insert(path.drop(1), child))
        )
    }
