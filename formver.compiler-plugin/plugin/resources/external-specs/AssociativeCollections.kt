package org.jetbrains.kotlin.formver.external.collections

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("kotlin.collections.map")
internal fun <K, V, R> (@Unique @Borrowed MapModel<out K, V>).mapEntriesModel(transform: (EntryModel<K, V>) -> R): @Unique ListModel<R> = TODO()

@ExternalSpec("kotlin.collections.Set")
internal interface SetModel<out E> : IterableModel<E> {
    @Law @Pure @Unique fun iterationSize(): Boolean = iterationSequence().size == size
    @Law @Pure @Unique fun distinctElements(): Boolean = forAll<Int> { i ->
        forAll<Int> { j ->
            (i >= 0 && i < iterationSequence().size && j >= 0 && j < iterationSequence().size && i < j) implies
                (iterationSequence()[i] != iterationSequence()[j])
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/size.html: number of elements.
    @Pure @get:Unique val size: Int
    @Law @Pure @Unique fun nonnegativeSize(): Boolean = size >= 0

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/is-empty.html: true iff empty.
    @Pure @Unique fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun emptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/contains.html: membership.
    @Pure @Unique fun contains(element: @UnsafeVariance E): Boolean
    @SpecOf("contains") @Unique fun containsSpec(element: @UnsafeVariance E) {
        postconditions<Boolean> { result ->
            result == exists<Int> { i -> i >= 0 && i < iterationSequence().size && element == iterationSequence()[i] }
            (element in iterationSequence()) implies result
        }
    }
}

@ExternalSpec("kotlin.collections.MutableSet")
internal interface MutableSetModel<E> : SetModel<E> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-set/add.html: insert if absent, returning whether inserted.
    @Unique @Borrowed fun add(element: E): Boolean
    @SpecOf("add") @Unique @Borrowed fun addSpec(element: E) {
        preconditions { size < 2147483647 }
        postconditions<Boolean> { result ->
            result == !old(contains(element))
            contains(element)
            size == old(size) + if (result) 1 else 0
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-set/remove.html: remove if present, returning whether removed.
    @Unique @Borrowed fun remove(element: E): Boolean
    @SpecOf("remove") @Unique @Borrowed fun removeSpec(element: E) {
        postconditions<Boolean> { result ->
            result == old(contains(element))
            !contains(element)
            size == old(size) - if (result) 1 else 0
        }
    }
}

@ExternalSpec("kotlin.collections.Map")
internal interface MapModel<K, out V> {
    @GhostObserver @Pure @Unique fun keysSequence(): IterationSequence<K> = ghostObserver()
    @GhostObserver @Pure @Unique fun iterationSequence(): IterationSequence<EntryModel<K, V>> = ghostObserver()
    @Law @Pure @Unique fun iterationSize(): Boolean =
        keysSequence().size == size && iterationSequence().size == keysSequence().size
    @Law @Pure @Unique fun iterationEntries(): Boolean = forAll<Int> { i ->
        (i >= 0 && i < keysSequence().size && i < iterationSequence().size) implies
            (iterationSequence()[i].key === keysSequence()[i] && iterationSequence()[i].value === get(keysSequence()[i]))
    }
    @Law @Pure @Unique fun distinctKeys(): Boolean = forAll<Int> { i ->
        forAll<Int> { j ->
            (i >= 0 && i < keysSequence().size && j >= 0 && j < keysSequence().size && i < j) implies
                (keysSequence()[i] != keysSequence()[j])
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/size.html: number of key/value pairs.
    @Pure @get:Unique val size: Int
    @Law @Pure @Unique fun nonnegativeSize(): Boolean = size >= 0

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/is-empty.html: true iff empty.
    @Pure @Unique fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun emptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/contains-key.html: whether the key is present.
    @Pure @Unique fun containsKey(key: K): Boolean
    @SpecOf("containsKey") @Unique fun keySpec(key: K) {
        postconditions<Boolean> { result ->
            result == exists<Int> { i -> i >= 0 && i < keysSequence().size && key == keysSequence()[i] }
            (key in keysSequence()) implies result
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/contains-value.html: whether any key maps to the value.
    @Pure @Unique fun containsValue(value: @UnsafeVariance V): Boolean
    @SpecOf("containsValue") @Unique fun valueSpec(value: @UnsafeVariance V) {
        postconditions<Boolean> { result ->
            result == exists<Int> { i -> i >= 0 && i < keysSequence().size && value == get(keysSequence()[i]) }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/get.html: mapped value or null; null can also be a mapped value.
    @Pure @Unique operator fun get(key: K): V?
    @SpecOf("get") @Unique fun getSpec(key: K) {
        postconditions<V?> { result ->
            !containsKey(key) implies (result == null)
        }
    }
}

@ExternalSpec("kotlin.collections.MutableMap")
internal interface MutableMapModel<K, V> : MapModel<K, V> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-map/put.html: associate the key, returning its previous value or null.
    @Unique @Borrowed fun put(key: K, value: V): V?
    @SpecOf("put") @Unique @Borrowed fun putSpec(key: K, value: V) {
        preconditions { size < 2147483647 }
        postconditions<V?> { result ->
            result === old(get(key))
            containsKey(key)
            get(key) === value
            containsValue(value)
            size == old(size) + if (old(containsKey(key))) 0 else 1
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-map/remove.html: remove the key, returning its previous value or null.
    @Unique @Borrowed fun remove(key: K): V?
    @SpecOf("remove") @Unique @Borrowed fun removeSpec(key: K) {
        postconditions<V?> { result ->
            result === old(get(key))
            !containsKey(key)
            size == old(size) - if (old(containsKey(key))) 1 else 0
        }
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/set-of.html: empty read-only set.
@ExternalFunction("kotlin.collections.setOf")
internal fun <T> emptySetModel(): @Unique SetModel<T> = TODO()
@SpecOf("emptySetModel") internal fun <T> emptySetSpec() {
    postconditions<@Unique SetModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/set-of.html: singleton read-only set.
@ExternalFunction("kotlin.collections.setOf")
internal fun <T> singletonSetModel(element: T): @Unique SetModel<T> = TODO()
@SpecOf("singletonSetModel") internal fun <T> singletonSetSpec(element: T) {
    postconditions<@Unique SetModel<T>> { result -> result.size == 1; result.contains(element) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/mutable-set-of.html: new empty mutable set.
@ExternalFunction("kotlin.collections.mutableSetOf")
internal fun <T> emptyMutableSetModel(): @Unique MutableSetModel<T> = TODO()
@SpecOf("emptyMutableSetModel") internal fun <T> emptyMutableSetSpec() {
    postconditions<@Unique MutableSetModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/map-of.html: empty read-only map.
@ExternalFunction("kotlin.collections.mapOf")
internal fun <K, V> emptyMapModel(): @Unique MapModel<K, V> = TODO()
@SpecOf("emptyMapModel") internal fun <K, V> emptyMapSpec() {
    postconditions<@Unique MapModel<K, V>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/mutable-map-of.html: new empty mutable map.
@ExternalFunction("kotlin.collections.mutableMapOf")
internal fun <K, V> emptyMutableMapModel(): @Unique MutableMapModel<K, V> = TODO()
@SpecOf("emptyMutableMapModel") internal fun <K, V> emptyMutableMapSpec() {
    postconditions<@Unique MutableMapModel<K, V>> { result -> result.size == 0 }
}
