package org.jetbrains.kotlin.formver.external.collections

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("kotlin.collections.List")
interface ListModel<out E> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/size.html: number of elements.
    @Pure @get:Unique val size: Int
    @Law @Pure @Unique fun nonnegativeSize(): Boolean = size >= 0

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/is-empty.html: true iff empty.
    @Pure @Unique fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun emptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/contains.html: membership.
    @Pure @Unique fun contains(element: @UnsafeVariance E): Boolean
    @SpecOf("contains") @Unique fun containsSpec(element: @UnsafeVariance E) {
        postconditions<Boolean> { result ->
            result == exists<Int> { i -> i >= 0 && i < size && get(i) == element }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/get.html: element at the index; bounds are [0, size).
    @Pure @Unique operator fun get(index: Int): E
    @SpecOf("get") @Unique fun getSpec(index: Int) {
        preconditions { index >= 0 && index < size }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index-of.html: first index, or -1 if absent.
    @Pure @Unique fun indexOf(element: @UnsafeVariance E): Int
    @SpecOf("indexOf") @Unique fun indexSpec(element: @UnsafeVariance E) {
        postconditions<Int> { result ->
            result >= -1 && result < size
            (result == -1) == !contains(element)
            (result >= 0) implies (get(result) == element)
            forAll<Int> { i -> (i >= 0 && i < result) implies (get(i) != element) }
        }
    }
}

@ExternalSpec("kotlin.collections.MutableList")
interface MutableListModel<E> : ListModel<E> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/add.html: append and return true.
    @Unique @Borrowed fun add(element: E): Boolean
    @SpecOf("add") @Unique @Borrowed fun addSpec(element: E) {
        preconditions { size < 2147483647 }
        postconditions<Boolean> { result ->
            result
            size == old(size) + 1
            get(old(size)) == element
            forAll<Int> { i -> (i >= 0 && i < old(size)) implies (get(i) == old(get(i))) }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/add.html: insert at index, shifting the following elements right.
    @Unique @Borrowed fun add(index: Int, element: E)
    @SpecOf("add") @Unique @Borrowed fun insertSpec(index: Int, element: E) {
        preconditions { index >= 0 && index <= size; size < 2147483647 }
        postconditions<Unit> {
            size == old(size) + 1
            get(index) == element
            forAll<Int> { i ->
                (i >= 0 && i < size && i != index) implies
                    (get(i) == if (i < index) old(get(i)) else old(get(i - 1)))
            }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/set.html: replace, returning the previous element.
    @Unique @Borrowed operator fun set(index: Int, element: E): E
    @SpecOf("set") @Unique @Borrowed fun setSpec(index: Int, element: E) {
        preconditions { index >= 0 && index < size }
        postconditions<E> { result ->
            result == old(get(index))
            size == old(size)
            get(index) == element
            forAll<Int> { i -> (i >= 0 && i < size && i != index) implies (get(i) == old(get(i))) }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/remove.html: remove the first occurrence, returning whether it was present.
    @Unique @Borrowed fun remove(element: E): Boolean
    @SpecOf("remove") @Unique @Borrowed fun removeSpec(element: E) {
        postconditions<Boolean> { result ->
            result == old(contains(element))
            size == old(size) - if (result) 1 else 0
            forAll<Int> { i ->
                (i >= 0 && i < size) implies
                    (get(i) == if (result && i >= old(indexOf(element))) old(get(i + 1)) else old(get(i)))
            }
        }
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/list-of.html: empty read-only list.
@ExternalFunction("kotlin.collections.listOf")
fun <T> emptyListModel(): @Unique ListModel<T> = TODO()
@SpecOf("emptyListModel") fun <T> emptyListSpec() {
    postconditions<@Unique ListModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/list-of.html: read-only singleton.
@ExternalFunction("kotlin.collections.listOf")
fun <T> singletonListModel(element: T): @Unique ListModel<T> = TODO()
@SpecOf("singletonListModel") fun <T> singletonListSpec(element: T) {
    postconditions<@Unique ListModel<T>> { result -> result.size == 1; result[0] == element }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/mutable-list-of.html: new empty mutable list.
@ExternalFunction("kotlin.collections.mutableListOf")
fun <T> emptyMutableListModel(): @Unique MutableListModel<T> = TODO()
@SpecOf("emptyMutableListModel") fun <T> emptyMutableListSpec() {
    postconditions<@Unique MutableListModel<T>> { result -> result.size == 0 }
}
