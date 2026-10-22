package org.jetbrains.kotlin.formver.external.arraydeque

import org.jetbrains.kotlin.formver.plugin.*

// Contract correspondence: ArrayDequeSpecEqualityTest; proof scope: docs/targets/arraydeque-plan.md.
@ExternalSpec("kotlin.collections.ArrayDeque")
internal interface ArrayDequeModel<E> {
    @Pure @get:Unique val size: Int
    @Law @Pure @Unique fun sizeBounds(): Boolean = 0 <= size

    @Pure @Unique operator fun get(index: Int): E
    @SpecOf("get") @Unique fun getSpec(index: Int) {
        preconditions { 0 <= index && index < size }
    }

    @Pure @Unique fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun emptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }

    @Unique @Borrowed fun first(): E
    @SpecOf("first") @Unique @Borrowed fun firstSpec() {
        postconditions<E> { r ->
            size == old(size)
            (size > 0) implies (r === get(0))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun firstOrNull(): E?
    @SpecOf("firstOrNull") @Unique @Borrowed fun firstOrNullSpec() {
        postconditions<E?> { r ->
            size == old(size)
            (size == 0) implies (r === null)
            (size > 0) implies (r === get(0))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun last(): E
    @SpecOf("last") @Unique @Borrowed fun lastSpec() {
        postconditions<E> { r ->
            size == old(size)
            (size > 0) implies (r === get(size - 1))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun lastOrNull(): E?
    @SpecOf("lastOrNull") @Unique @Borrowed fun lastOrNullSpec() {
        postconditions<E?> { r ->
            size == old(size)
            (size == 0) implies (r === null)
            (size > 0) implies (r === get(size - 1))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun addFirst(element: E)
    @SpecOf("addFirst") @Unique @Borrowed fun addFirstSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Unit> {
            size == old(size) + 1
            (size > 0) implies (get(0) === element)
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (get(i + 1) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun addLast(element: E)
    @SpecOf("addLast") @Unique @Borrowed fun addLastSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Unit> {
            size == old(size) + 1
            (size > 0) implies (get(size - 1) === element)
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun removeFirst(): E
    @SpecOf("removeFirst") @Unique @Borrowed fun removeFirstSpec() {
        postconditions<E> { r ->
            old(size) > 0
            (old(size) > 0) implies (size == old(size) - 1 && r === old(get(0)))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i + 1))) }
        }
    }

    @Unique @Borrowed fun removeFirstOrNull(): E?
    @SpecOf("removeFirstOrNull") @Unique @Borrowed fun removeFirstOrNullSpec() {
        postconditions<E?> { r ->
            (old(size) == 0) implies (size == 0)
            (old(size) > 0) implies (size == old(size) - 1)
            (old(size) == 0) implies (r === null)
            (old(size) > 0) implies (r === old(get(0)))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i + 1))) }
        }
    }

    @Unique @Borrowed fun removeLast(): E
    @SpecOf("removeLast") @Unique @Borrowed fun removeLastSpec() {
        postconditions<E> { r ->
            old(size) > 0
            (old(size) > 0) implies (size == old(size) - 1 && r === old(get(size - 1)))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun removeLastOrNull(): E?
    @SpecOf("removeLastOrNull") @Unique @Borrowed fun removeLastOrNullSpec() {
        postconditions<E?> { r ->
            (old(size) == 0) implies (size == 0)
            (old(size) > 0) implies (size == old(size) - 1)
            (old(size) == 0) implies (r === null)
            (old(size) > 0) implies (r === old(get(size - 1)))
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun add(element: E): Boolean
    @Suppress("UNUSED_EXPRESSION")
    @SpecOf("add") @Unique @Borrowed fun addSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Boolean> { r ->
            r
            size == old(size) + 1
            (size > 0) implies (get(size - 1) === element)
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun contains(element: E): Boolean
    @SpecOf("contains") @Unique @Borrowed fun containsSpec(element: E) {
        postconditions<Boolean> { r ->
            r == exists<Int> { i -> (0 <= i && i < size) && element == get(i) }
            size == old(size)
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun indexOf(element: E): Int
    @SpecOf("indexOf") @Unique @Borrowed fun indexOfSpec(element: E) {
        postconditions<Int> { result ->
            result >= -1
            result < size
            (result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == get(i) }
            (result >= 0) implies (element == get(result))
            forAll<Int> { i -> (0 <= i && i < result) implies (element != get(i)) }
            size == old(size)
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun lastIndexOf(element: E): Int
    @SpecOf("lastIndexOf") @Unique @Borrowed fun lastIndexOfSpec(element: E) {
        postconditions<Int> { result ->
            result >= -1 && result < size
            (result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == get(i) }
            (result >= 0) implies (element == get(result))
            forAll<Int> { i -> (0 <= result && result < i && i < size) implies (element != get(i)) }
            size == old(size)
            forAll<Int> { i -> (0 <= i && i < size) implies (get(i) === old(get(i))) }
        }
    }

    @Unique @Borrowed fun clear()
    @SpecOf("clear") @Unique @Borrowed fun clearSpec() {
        postconditions<Unit> {
            size == 0
        }
    }

    @Unique @Borrowed operator fun set(index: Int, element: E): E
    @SpecOf("set") @Unique @Borrowed fun setSpec(index: Int, element: E) {
        postconditions<E> { r ->
            size == old(size)
            (0 <= index && index < size) implies (r === old(get(index)) && get(index) === element)
            forAll<Int> { i -> (0 <= i && i < size && i != index) implies (get(i) === old(get(i))) }
        }
    }
}

@ExternalConstructor("kotlin.collections.ArrayDeque")
internal fun <E> arrayDequeModel(): @Unique ArrayDequeModel<E> = TODO()

@SpecOf("arrayDequeModel") internal fun <E> arrayDequeSpec() {
    postconditions<@Unique ArrayDequeModel<E>> { result -> result.size == 0 }
}
