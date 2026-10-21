/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// FULL_JDK
// RETURN_VALUE_CHECKER_MODE: CHECKER
@file:Suppress("RETURN_VALUE_NOT_USED", "NOTHING_TO_INLINE")
import org.jetbrains.kotlin.formver.plugin.*

// D-8: Source-port specifications; residual trust: upstream/kotlin-v2.3.0/PROVENANCE.md.
interface DequeSpec<E> {
    @Pure @get:Unique val size: Int
    @Pure @Unique fun <!VIPER_TEXT!>elementAt<!>(index: Int): E
    @SpecOf("elementAt") @Unique fun elementAtSpec(index: Int) {
        preconditions { 0 <= index && index < size }
    }
}

// D-1: The source port has no AbstractMutableList supertype.
@SinceKotlin("1.4")
public <!VIPER_TEXT!>class ArrayDeque<!><E> : DequeSpec<E> {
    private var head: Int = 0
    private var elementData: @Unique Array<Any?>

    // D-2: The size observer reads private mutable storage.
    private var count: Int = 0
    @Pure @get:Unique override val size: Int <!VIPER_TEXT!>get<!>() = count

    // D-8: P1 and the restricted capacity domain.
    @Law @Pure @Unique fun representation(): Boolean =
        0 <= count && count <= elementData.size &&
        (elementData.size == 0 || (0 <= head && head < elementData.size)) &&
        (elementData.size != 0 || head == 0) && elementData.size <= 1073741824

    // D-7: The capacity constructor is outside the proved claim; see docs/targets/arraydeque-gaps-design.md (G12).
    /**
     * Constructs an empty deque.
     */
    public constructor() {
        // D-6: Unique empty storage substitutes for the shared sentinel.
        elementData = arrayOfNulls(0)
        verify(elementData.size == 0)
    }

    // D-7: This partial port contains the skeleton members.
    // D-5: InlineOnly is omitted from source helpers.
    @Pure @Unique private inline fun <!VIPER_TEXT!>internalGet<!>(internalIndex: Int): E {
        preconditions { 0 <= internalIndex && internalIndex < elementData.size }
        @Suppress("UNCHECKED_CAST")
        return elementData[internalIndex] as E
    }

    @SpecOf("positiveMod") @Unique private fun positiveModSpec(index: Int) {
        preconditions { elementData.size > 0 && 0 <= index && index - elementData.size < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (index >= elementData.size) index - elementData.size else index)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>positiveMod<!>(index: Int): Int = if (index >= elementData.size) index - elementData.size else index

    @SpecOf("negativeMod") @Unique private fun negativeModSpec(index: Int) {
        preconditions { elementData.size > 0 && -elementData.size <= index && index < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (index < 0) index + elementData.size else index)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>negativeMod<!>(index: Int): Int = if (index < 0) index + elementData.size else index

    @SpecOf("internalIndex") @Unique private fun internalIndexSpec(index: Int) {
        preconditions { elementData.size > 0 && 0 <= index && index <= size && head + index - elementData.size < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (head + index >= elementData.size) head + index - elementData.size else head + index)
        }
    }
    // D-5: InlineOnly is omitted from source helpers.
    @Pure @Unique private inline fun <!VIPER_TEXT!>internalIndex<!>(index: Int): Int = positiveMod(head + index)

    @SpecOf("incremented") @Unique private fun incrementedSpec(index: Int) {
        preconditions { 0 <= index && index < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (index == elementData.size - 1) 0 else index + 1)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>incremented<!>(index: Int): Int = if (index == elementData.lastIndex) 0 else index + 1

    @SpecOf("decremented") @Unique private fun decrementedSpec(index: Int) {
        preconditions { 0 <= index && index < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (index == 0) elementData.size - 1 else index - 1)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>decremented<!>(index: Int): Int = if (index == 0) elementData.lastIndex else index - 1

    // D-8: A non-throwing observer for logical window offsets.
    @Pure @Unique override fun <!VIPER_TEXT!>elementAt<!>(index: Int): E {
        preconditions { 0 <= index && index < size }
        val physical = positiveMod(head + index)
        @Suppress("UNCHECKED_CAST")
        return elementData[physical] as E
    }

    // D-1: isEmpty has no inherited declaration to override.
    @Pure @Unique fun <!VIPER_TEXT!>isEmpty<!>(): Boolean = size == 0

    // D-4: Private copies of collection observers; count is the D-2 storage for size.
    @Pure @Unique private fun <!VIPER_TEXT!>isNotEmpty<!>(): Boolean = !isEmpty()
    @Pure @get:Unique private val lastIndex: Int <!VIPER_TEXT!>get<!>() = count - 1

    @SpecOf("first") @Unique @Borrowed fun firstSpec() {
        postconditions<E> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (size > 0) implies (r === elementAt(0))
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    /**
     * Returns the first element, or throws [NoSuchElementException] if this deque is empty.
     */
    @Unique @Borrowed public fun <!VIPER_TEXT!>first<!>(): E = if (isEmpty()) throw NoSuchElementException("ArrayDeque is empty.") else internalGet(head)

    @SpecOf("firstOrNull") @Unique @Borrowed fun firstOrNullSpec() {
        postconditions<E?> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (size == 0) implies (r === null)
            (size > 0) implies (r === elementAt(0))
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    /**
     * Returns the first element, or `null` if this deque is empty.
     */
    @Unique @Borrowed public fun <!VIPER_TEXT!>firstOrNull<!>(): E? = if (isEmpty()) null else internalGet(head)

    @SpecOf("last") @Unique @Borrowed fun lastSpec() {
        postconditions<E> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (size > 0) implies (r === elementAt(size - 1))
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    /**
     * Returns the last element, or throws [NoSuchElementException] if this deque is empty.
     */
    @Unique @Borrowed public fun <!VIPER_TEXT!>last<!>(): E = if (isEmpty()) throw NoSuchElementException("ArrayDeque is empty.") else internalGet(internalIndex(lastIndex))

    @SpecOf("lastOrNull") @Unique @Borrowed fun lastOrNullSpec() {
        postconditions<E?> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (size == 0) implies (r === null)
            (size > 0) implies (r === elementAt(size - 1))
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    /**
     * Returns the last element, or `null` if this deque is empty.
     */
    @Unique @Borrowed public fun <!VIPER_TEXT!>lastOrNull<!>(): E? = if (isEmpty()) null else internalGet(internalIndex(lastIndex))

    @SpecOf("get") @Unique @Borrowed fun getSpec(index: Int) {
        postconditions<E> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (0 <= index && index < size) implies (r === elementAt(index))
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    // D-1: List operations have no inherited declaration to override.
    @Unique @Borrowed public operator fun <!VIPER_TEXT!>get<!>(index: Int): E {
        // D-4: The index check is a private copy.
        checkElementIndex(index, size)

        return internalGet(internalIndex(index))
    }

    @SpecOf("set") @Unique @Borrowed fun setSpec(index: Int, element: E) {
        postconditions<E> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (0 <= index && index < size) implies (r === old(elementAt(index)) && elementAt(index) === element)
            forAll<Int> { i -> (0 <= i && i < size && i != index) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    @IgnorableReturnValue
    @Unique @Borrowed public operator fun <!VIPER_TEXT!>set<!>(index: Int, element: E): E {
        // D-4: The index check is a private copy.
        checkElementIndex(index, size)

        val internalIndex = internalIndex(index)
        val oldElement = internalGet(internalIndex)
        elementData[internalIndex] = element

        return oldElement
    }
}

// D-4: Private copies of AbstractList index checks.
private fun <!VIPER_TEXT!>checkElementIndex<!>(index: Int, size: Int) {
    postconditions<Unit> { 0 <= index && index < size }
    if (index < 0 || index >= size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

private fun <!VIPER_TEXT!>checkPositionIndex<!>(index: Int, size: Int) {
    postconditions<Unit> { 0 <= index && index <= size }
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

// D-8: Public-entry proofs and negative siblings for the partial source port.

fun <!VIPER_TEXT!>emptyDeque<!>() {
    val d: @Unique ArrayDeque<Any?> = ArrayDeque()
    verify(d.size == 0)
    val a = d.firstOrNull()
    val b = d.lastOrNull()
    verify(a === null && b === null)
}

fun <E> <!VIPER_TEXT!>access<!>(d: @Unique @Borrowed ArrayDeque<E>, index: Int, other: Int, value: E) {
    preconditions { 0 <= index && index < d.size && 0 <= other && other < d.size && index != other }
    val n = d.size
    val before = d[index]
    val untouched = d[other]
    val replaced = d.set(index, value)
    verify(replaced === before)
    val after = d[index]
    val preserved = d[other]
    verify(after === value && preserved === untouched && d.size == n)
    val first = d.first()
    val last = d.last()
    val firstNullable = d.firstOrNull()
    val lastNullable = d.lastOrNull()
    verify(first === d.elementAt(0) && last === d.elementAt(d.size - 1))
    verify(firstNullable === first && lastNullable === last)
}

fun <E> <!VIPER_TEXT!>firstOnEmpty<!>(d: @Unique @Borrowed ArrayDeque<E>) {
    preconditions { d.size == 0 }
    val first = d.firstOrNull()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first !== null<!>)
}

// The control wraps offsets crossing the physical end of the backing array.
fun <E> <!VIPER_TEXT!>getCircular<!>(data: @Unique @Borrowed Array<E>, head: Int, index: Int): E {
    preconditions { data.size > 0 && data.size <= 1073741824 && 0 <= head && head < data.size && 0 <= index && index < data.size }
    val physical = head + index
    return data[if (physical >= data.size) physical - data.size else physical]
}

// Omitting positiveMod leaves a wrapped logical offset past the physical end.
fun <E> <!VIPER_TEXT!>getWrapped<!>(data: @Unique @Borrowed Array<E>, head: Int, index: Int): E {
    preconditions { data.size > 0 && data.size <= 1073741824 && 0 <= head && head < data.size && 0 <= index && index < data.size }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>data[head + index]<!>
}
