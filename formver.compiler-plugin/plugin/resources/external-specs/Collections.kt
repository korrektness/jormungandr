package org.jetbrains.kotlin.formver.external.collections

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("kotlin.collections.List")
internal interface ListModel<out E> : CollectionModel<E> {
    @Law @Pure @Unique fun iterationElements(): Boolean =
        iterationSequence().size == size && forAll<Int> { i ->
            (i >= 0 && i < iterationSequence().size && i < size) implies (iterationSequence()[i] === get(i))
        }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/size.html: number of elements.
    @Pure @get:Unique override val size: Int

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/is-empty.html: true iff empty.
    @Pure @Unique override fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun emptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/contains.html: membership.
    @Pure @Unique fun contains(element: @UnsafeVariance E): Boolean
    @SpecOf("contains") @Unique fun containsSpec(element: @UnsafeVariance E) {
        postconditions<Boolean> { result ->
            result == exists<Int> { i -> i >= 0 && i < size && element == get(i) }
            forAll<Int> { i -> (i >= 0 && i < size && get(i) === element) implies result }
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
            (result >= 0) implies (element == get(result))
            forAll<Int> { i -> (i >= 0 && i < result) implies (element != get(i)) }
        }
    }
}

@ExternalFunction("kotlin.collections.map")
internal fun <T, R> (@Unique @Borrowed IterableModel<T>).mapModel(transform: (T) -> R): @Unique ListModel<R> = TODO()

@ExternalFunction("kotlin.collections.filter")
internal fun <T> (@Unique @Borrowed IterableModel<T>).filterModel(predicate: (T) -> Boolean): @Unique ListModel<T> = TODO()

@ExternalSpec("kotlin.collections.MutableList")
internal interface MutableListModel<E> : ListModel<E> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/add.html: append and return true.
    @Unique @Borrowed fun add(element: E): Boolean
    @SpecOf("add") @Unique @Borrowed fun addSpec(element: E) {
        preconditions { size < 2147483647 }
        postconditions<Boolean> { result ->
            result == true
            size == old(size) + 1
            get(old(size)) === element
            forAll<Int> { i -> (i >= 0 && i < old(size)) implies (get(i) === old(get(i))) }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/add.html: insert at index, shifting the following elements right.
    @Unique @Borrowed fun add(index: Int, element: E)
    @SpecOf("add") @Unique @Borrowed fun insertSpec(index: Int, element: E) {
        preconditions { index >= 0 && index <= size; size < 2147483647 }
        postconditions<Unit> {
            size == old(size) + 1
            get(index) === element
            forAll<Int> { i ->
                (i >= 0 && i < size && i != index) implies
                    (get(i) === if (i < index) old(get(i)) else old(get(i - 1)))
            }
        }
    }

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-list/set.html: replace, returning the previous element.
    @Unique @Borrowed operator fun set(index: Int, element: E): E
    @SpecOf("set") @Unique @Borrowed fun setSpec(index: Int, element: E) {
        preconditions { index >= 0 && index < size }
        postconditions<E> { result ->
            result === old(get(index))
            size == old(size)
            get(index) === element
            forAll<Int> { i -> (i >= 0 && i < size && i != index) implies (get(i) === old(get(i))) }
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
                    (get(i) === if (result && i >= old(indexOf(element))) old(get(i + 1)) else old(get(i)))
            }
        }
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/list-of.html: empty read-only list.
@ExternalFunction("kotlin.collections.listOf")
internal fun <T> emptyListModel(): @Unique ListModel<T> = TODO()
@SpecOf("emptyListModel") internal fun <T> emptyListSpec() {
    postconditions<@Unique ListModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/empty-list.html: empty read-only list.
@ExternalFunction("kotlin.collections.emptyList")
internal fun <T> emptyListFunctionModel(): @Unique ListModel<T> = TODO()
@SpecOf("emptyListFunctionModel") internal fun <T> emptyListFunctionSpec() {
    postconditions<@Unique ListModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/list-of.html: read-only singleton.
@ExternalFunction("kotlin.collections.listOf")
internal fun <T> singletonListModel(element: T): @Unique ListModel<T> = TODO()
@SpecOf("singletonListModel") internal fun <T> singletonListSpec(element: T) {
    postconditions<@Unique ListModel<T>> { result -> result.size == 1; result[0] === element }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/mutable-list-of.html: new empty mutable list.
@ExternalFunction("kotlin.collections.mutableListOf")
internal fun <T> emptyMutableListModel(): @Unique MutableListModel<T> = TODO()
@SpecOf("emptyMutableListModel") internal fun <T> emptyMutableListSpec() {
    postconditions<@Unique MutableListModel<T>> { result -> result.size == 0 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/last-index.html: index of the last element, -1 when
// empty.
@ExternalProperty("kotlin.collections.lastIndex")
@Pure internal val <T> (@Unique @Borrowed ListModel<T>).lastIndexModel: Int
    get() = TODO()
@SpecOf("lastIndexModel") internal fun <T> (@Unique @Borrowed ListModel<T>).lastIndexSpec() {
    postconditions<Int> { result -> result == size - 1 }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/indices.html: the range of valid indices.
@ExternalProperty("kotlin.collections.indices")
@Pure internal val (@Unique @Borrowed CollectionModel<*>).indicesModel: IntRange
    get() = TODO()

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/is-not-empty.html: true iff not empty.
@ExternalFunction("kotlin.collections.isNotEmpty")
@Pure internal fun <T> (@Unique @Borrowed CollectionModel<T>).isNotEmptyModel(): Boolean = TODO()
@SpecOf("isNotEmptyModel") internal fun <T> (@Unique @Borrowed CollectionModel<T>).isNotEmptySpec() {
    postconditions<Boolean> { result -> result == (size > 0) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/count.html: number of elements.
@ExternalFunction("kotlin.collections.count")
@Pure internal fun <T> (@Unique @Borrowed CollectionModel<T>).countModel(): Int = TODO()
@SpecOf("countModel") internal fun <T> (@Unique @Borrowed CollectionModel<T>).countSpec() {
    postconditions<Int> { result -> result == size }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/first.html: first element; throws when empty.
@ExternalFunction("kotlin.collections.first")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).firstModel(): T = TODO()
@SpecOf("firstModel") internal fun <T> (@Unique @Borrowed ListModel<T>).firstSpec() {
    preconditions { size > 0 }
    postconditions<T> { result -> result == get(0) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/last.html: last element; throws when empty.
@ExternalFunction("kotlin.collections.last")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).lastModel(): T = TODO()
@SpecOf("lastModel") internal fun <T> (@Unique @Borrowed ListModel<T>).lastSpec() {
    preconditions { size > 0 }
    postconditions<T> { result -> result == get(size - 1) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/single.html: the only element; throws unless there
// is exactly one.
@ExternalFunction("kotlin.collections.single")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).singleModel(): T = TODO()
@SpecOf("singleModel") internal fun <T> (@Unique @Borrowed ListModel<T>).singleSpec() {
    preconditions { size == 1 }
    postconditions<T> { result -> result == get(0) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/element-at.html: element at the index; bounds are
// [0, size).
@ExternalFunction("kotlin.collections.elementAt")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).elementAtModel(index: Int): T = TODO()
@SpecOf("elementAtModel") internal fun <T> (@Unique @Borrowed ListModel<T>).elementAtSpec(index: Int) {
    preconditions { index >= 0 && index < size }
    postconditions<T> { result -> result == get(index) }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/first-or-null.html: first element, null when empty.
@ExternalFunction("kotlin.collections.firstOrNull")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).firstOrNullModel(): T? = TODO()
@SpecOf("firstOrNullModel") internal fun <T> (@Unique @Borrowed ListModel<T>).firstOrNullSpec() {
    postconditions<T?> { result ->
        (size == 0) implies (result == null)
        (size > 0) implies (result == get(0))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/last-or-null.html: last element, null when empty.
@ExternalFunction("kotlin.collections.lastOrNull")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).lastOrNullModel(): T? = TODO()
@SpecOf("lastOrNullModel") internal fun <T> (@Unique @Borrowed ListModel<T>).lastOrNullSpec() {
    postconditions<T?> { result ->
        (size == 0) implies (result == null)
        (size > 0) implies (result == get(size - 1))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/single-or-null.html: the only element, null unless
// there is exactly one.
@ExternalFunction("kotlin.collections.singleOrNull")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).singleOrNullModel(): T? = TODO()
@SpecOf("singleOrNullModel") internal fun <T> (@Unique @Borrowed ListModel<T>).singleOrNullSpec() {
    postconditions<T?> { result ->
        (size != 1) implies (result == null)
        (size == 1) implies (result == get(0))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/get-or-null.html: element at the index, null outside
// [0, size).
@ExternalFunction("kotlin.collections.getOrNull")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).getOrNullModel(index: Int): T? = TODO()
@SpecOf("getOrNullModel") internal fun <T> (@Unique @Borrowed ListModel<T>).getOrNullSpec(index: Int) {
    postconditions<T?> { result ->
        (index < 0 || index >= size) implies (result == null)
        (index >= 0 && index < size) implies (result == get(index))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/element-at-or-null.html: element at the index, null
// outside [0, size).
@ExternalFunction("kotlin.collections.elementAtOrNull")
@Pure internal fun <T> (@Unique @Borrowed ListModel<T>).elementAtOrNullModel(index: Int): T? = TODO()
@SpecOf("elementAtOrNullModel") internal fun <T> (@Unique @Borrowed ListModel<T>).elementAtOrNullSpec(index: Int) {
    postconditions<T?> { result ->
        (index < 0 || index >= size) implies (result == null)
        (index >= 0 && index < size) implies (result == get(index))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/or-empty.html: the receiver, or an empty list for
// null.
@ExternalFunction("kotlin.collections.orEmpty")
internal fun <T> (@Unique ListModel<T>?).orEmptyModel(): @Unique ListModel<T> = TODO()
@SpecOf("orEmptyModel") internal fun <T> (@Unique ListModel<T>?).orEmptySpec() {
    postconditions<@Unique ListModel<T>> { result -> (this == null) implies (result.size == 0) }
}
