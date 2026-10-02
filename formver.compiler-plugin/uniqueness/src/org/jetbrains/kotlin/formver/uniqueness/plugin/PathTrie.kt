/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.kotlin.formver.type.plugin.TypeFactUnifier

/**
 * Persistent prefix trie of path components.
 *
 * A child entry `children[key]` represents the subtrie for all paths whose next component (relative to the current
 * node prefix) is `key`. Sibling children therefore represent alternative next components under the same prefix.
 *
 * For example, for a FIR access path like `a.b.c`, the trie contains:
 * `children[a] -> children[b] -> children[c]`.
 *
 * @property data value attached to the current path prefix.
 * @property children map keyed by the next path component.
 */
data class PathTrie<Key, Type>(
    val data: Type,
    val children: PersistentMap<Key, PathTrie<Key, Type>> = persistentMapOf(),
)

fun <Key, Type> PathTrie<Key, Type>.putChild(key: Key, child: PathTrie<Key, Type>): PathTrie<Key, Type> =
    copy(children = children.put(key, child))

val <Key> PathTrie<Key, *>.keys: Sequence<Key>
    get() = children.keys.asSequence() + children.values.flatMap { it.keys }

/** The number of components in the longest path of [this] trie. */
val PathTrie<*, *>.height: Int
    get() = children.values.maxOfOrNull { it.height + 1 } ?: 0

fun <Key, Type> PathTrie<Key, Type>.join(
    other: PathTrie<Key, Type>,
    typeUnifier: TypeFactUnifier<Type>,
): PathTrie<Key, Type> {
    var joinedChildren = children

    for ((key, otherChild) in other.children) {
        val child = joinedChildren[key]

        joinedChildren = joinedChildren.put(
            key,
            child?.join(otherChild, typeUnifier) ?: otherChild,
        )
    }

    return copy(
        data = typeUnifier.join(data, other.data),
        children = joinedChildren
    )
}

fun <Key, Type> PathTrie<Key, Type>.find(path: List<Key>): PathTrie<Key, Type>? {
    val head = path.firstOrNull()

    return if (head != null) {
        children[head]?.find(path.drop(1))
    } else {
        this
    }
}

fun <Key, Type> PathTrie<Key, Type>.enumerate(isTerminal: PathTrie<Key, Type>.() -> Boolean): Sequence<List<Key>> =
    enumerate(emptyList(), isTerminal)

fun <Key, Type> PathTrie<Key, Type>.enumerate(
    prefix: List<Key>,
    isTerminal: PathTrie<Key, Type>.() -> Boolean
): Sequence<List<Key>> =
    if (children.isEmpty()) {
        sequenceOf()
    } else {
        sequence {
            for ((key, child) in children) {
                val newPrefix = prefix + key

                if (child.isTerminal()) {
                    yield(newPrefix)
                }

                yieldAll(child.enumerate(newPrefix, isTerminal))
            }
        }
    }
