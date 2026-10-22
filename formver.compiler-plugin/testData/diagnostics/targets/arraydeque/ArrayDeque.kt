/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// FULL_JDK
// TRUST_REPORT
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

    // D-3: The inherited modification counter is a private field in the port.
    private var modCount: Int = 0

    @WrappingIntArithmetic @Unique @Borrowed
    private fun <!VIPER_TEXT!>registerModification<!>() {
        postconditions<Unit> {
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        modCount += 1
    }

    @Unique @Borrowed private fun <!VIPER_TEXT!>ensureCapacity<!>(minCapacity: Int) {
        preconditions { 0 <= minCapacity && minCapacity <= 715827882 }
        postconditions<Unit> {
            size == old(size)
            elementData.size >= minCapacity
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        if (minCapacity < 0) throw IllegalStateException("Deque is too big.")    // overflow
        if (minCapacity <= elementData.size) return
        // D-6: The sentinel identity test is represented by its size characterization.
        if (elementData.size == 0) {
            elementData = arrayOfNulls(minCapacity.coerceAtLeast(defaultMinCapacity))
            verify(elementData.size > 0)
            return
        }

        // D-4: The AbstractList capacity helper is a private copy.
        val newCapacity = newCapacity(elementData.size, minCapacity)
        copyElements(newCapacity)
    }

    @Unique @Borrowed private fun <!VIPER_TEXT!>copyElements<!>(newCapacity: Int) {
        preconditions { elementData.size < newCapacity && newCapacity <= 1073741824 }
        postconditions<Unit> {
            size == old(size)
            elementData.size == newCapacity
            head == 0
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        val newElements = arrayOfNulls<Any?>(newCapacity)
        elementData.copyInto(newElements, 0, head, elementData.size)
        elementData.copyInto(newElements, elementData.size - head, 0, head)
        head = 0
        elementData = newElements
        verify(elementData.size > 0)
    }

    // D-7: Members outside the partial port are omitted.
    // D-5: InlineOnly is omitted from source helpers.
    @Pure @Unique private inline fun <!VIPER_TEXT!>internalGet<!>(internalIndex: Int): E {
        preconditions { 0 <= internalIndex && internalIndex < elementData.size }
        @Suppress("UNCHECKED_CAST")
        return elementData[internalIndex] as E
    }

    @SpecOf("positiveMod") @Unique private fun positiveModSpec(index: Int) {
        preconditions { 0 <= index && (if (elementData.size == 0) index == 0 else index - elementData.size < elementData.size) }
        postconditions<Int> { r ->
            0 <= r && (if (elementData.size == 0) r == 0 else r < elementData.size)
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
        preconditions { 0 <= index && index <= size && (elementData.size == 0 || head + index - elementData.size < elementData.size) }
        postconditions<Int> { r ->
            0 <= r && (if (elementData.size == 0) r == 0 else r < elementData.size)
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
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
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
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
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
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
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
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    /**
     * Returns the last element, or `null` if this deque is empty.
     */
    @Unique @Borrowed public fun <!VIPER_TEXT!>lastOrNull<!>(): E? = if (isEmpty()) null else internalGet(internalIndex(lastIndex))

    @SpecOf("addFirst") @Unique @Borrowed fun addFirstSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Unit> {
            size == old(size) + 1
            (size > 0) implies (elementAt(0) === element)
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (elementAt(i + 1) === old(elementAt(i))) }
        }
    }
    @Unique @Borrowed public fun <!VIPER_TEXT!>addFirst<!>(element: E) {
        registerModification()
        ensureCapacity(size + 1)

        head = decremented(head)
        elementData[head] = element
        // D-2: count stores the mutable size.
        count += 1
    }

    @SpecOf("addLast") @Unique @Borrowed fun addLastSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Unit> {
            size == old(size) + 1
            (size > 0) implies (elementAt(size - 1) === element)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    @Unique @Borrowed public fun <!VIPER_TEXT!>addLast<!>(element: E) {
        registerModification()
        ensureCapacity(size + 1)

        elementData[internalIndex(size)] = element
        // D-2: count stores the mutable size.
        count += 1
    }

    @SpecOf("removeFirst") @Unique @Borrowed fun removeFirstSpec() {
        postconditions<E> { r ->
            old(size) > 0
            (old(size) > 0) implies (size == old(size) - 1 && r === old(elementAt(0)))
            elementData.size == old(elementData.size)
            elementData[old(head)] === null
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i + 1))) }
        }
    }
    @IgnorableReturnValue
    @Unique @Borrowed public fun <!VIPER_TEXT!>removeFirst<!>(): E {
        if (isEmpty()) throw NoSuchElementException("ArrayDeque is empty.")
        registerModification()

        val element = internalGet(head)
        elementData[head] = null
        head = incremented(head)
        // D-2: count stores the mutable size.
        count -= 1
        return element
    }

    @SpecOf("removeFirstOrNull") @Unique @Borrowed fun removeFirstOrNullSpec() {
        postconditions<E?> { r ->
            (old(size) == 0) implies (size == 0)
            (old(size) > 0) implies (size == old(size) - 1)
            elementData.size == old(elementData.size)
            (old(size) == 0) implies (r === null)
            (old(size) > 0) implies (r === old(elementAt(0)) && elementData[old(head)] === null)
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i + 1))) }
        }
    }
    @IgnorableReturnValue
    @Unique @Borrowed public fun <!VIPER_TEXT!>removeFirstOrNull<!>(): E? = if (isEmpty()) null else removeFirst()

    @SpecOf("removeLast") @Unique @Borrowed fun removeLastSpec() {
        postconditions<E> { r ->
            old(size) > 0
            (old(size) > 0) implies (size == old(size) - 1 && r === old(elementAt(size - 1)))
            elementData.size == old(elementData.size)
            elementData[old(internalIndex(lastIndex))] === null
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    @IgnorableReturnValue
    @Unique @Borrowed public fun <!VIPER_TEXT!>removeLast<!>(): E {
        if (isEmpty()) throw NoSuchElementException("ArrayDeque is empty.")
        registerModification()

        val internalLastIndex = internalIndex(lastIndex)
        val element = internalGet(internalLastIndex)
        elementData[internalLastIndex] = null
        // D-2: count stores the mutable size.
        count -= 1
        return element
    }

    @SpecOf("removeLastOrNull") @Unique @Borrowed fun removeLastOrNullSpec() {
        postconditions<E?> { r ->
            (old(size) == 0) implies (size == 0)
            (old(size) > 0) implies (size == old(size) - 1)
            elementData.size == old(elementData.size)
            (old(size) == 0) implies (r === null)
            (old(size) > 0) implies (r === old(elementAt(size - 1)) && elementData[old(internalIndex(lastIndex))] === null)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    @IgnorableReturnValue
    @Unique @Borrowed public fun <!VIPER_TEXT!>removeLastOrNull<!>(): E? = if (isEmpty()) null else removeLast()

    @SpecOf("add") @Unique @Borrowed fun addSpec(element: E) {
        preconditions { size < 715827882 }
        postconditions<Boolean> { r ->
            r
            size == old(size) + 1
            (size > 0) implies (elementAt(size - 1) === element)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    // D-1: add has no inherited declaration to override.
    @IgnorableReturnValue
    @Unique @Borrowed public fun <!VIPER_TEXT!>add<!>(element: E): Boolean {
        addLast(element)
        return true
    }

    @SpecOf("contains") @Unique @Borrowed fun containsSpec(element: E) {
        postconditions<Boolean> { r ->
            r == exists<Int> { i -> (0 <= i && i < size) && element == elementAt(i) }
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
    }
    // D-1: Search members have no inherited declarations to override.
    @Unique @Borrowed public fun <!VIPER_TEXT!>contains<!>(element: E): Boolean = indexOf(element) != -1

    // D-8: Membership clauses expand containsSpec's relation inside specifications.
    // D4 trust is scoped to executed equality on E; see upstream/kotlin-v2.3.0/PROVENANCE.md.
    @TrustedElementEquality @Unique @Borrowed public fun <!VIPER_TEXT!>indexOf<!>(element: E): Int {
        postconditions<Int> { result ->
            result >= -1
            result < size
            (result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == elementAt(i) }
            (result >= 0) implies (element == elementAt(result))
            forAll<Int> { i -> (0 <= i && i < result) implies (element != elementAt(i)) }
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        val tail = internalIndex(size)

        if (head < tail) {
            for (index in head until tail) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head <= index && index <= tail
                    forAll<Int> { k -> (0 <= k && k < size && k < index - head) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index - head
            }
        } else if (isNotEmpty() && head >= tail) {
            for (index in head until elementData.size) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head <= index && index <= elementData.size
                    forAll<Int> { k -> (0 <= k && k < size && k < index - head) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index - head
            }
            for (index in 0 until tail) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    0 <= index && index <= tail
                    forAll<Int> { k -> (0 <= k && k < size && k < index + elementData.size - head) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index + elementData.size - head
            }
        }

        return -1
    }

    @TrustedElementEquality @Unique @Borrowed public fun <!VIPER_TEXT!>lastIndexOf<!>(element: E): Int {
        postconditions<Int> { result ->
            result >= -1 && result < size
            (result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == elementAt(i) }
            (result >= 0) implies (element == elementAt(result))
            forAll<Int> { i -> (0 <= result && result < i && i < size) implies (element != elementAt(i)) }
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        val tail = internalIndex(size)

        if (head < tail) {
            for (index in tail - 1 downTo head) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head - 1 <= index && index < tail
                    forAll<Int> { k -> (0 <= k && index - head < k && k < size) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index - head
            }
        } else if (isNotEmpty() && head >= tail) {
            for (index in tail - 1 downTo 0) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    -1 <= index && index < tail
                    forAll<Int> { k -> (0 <= k && index + elementData.size - head < k && k < size) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index + elementData.size - head
            }
            for (index in elementData.lastIndex downTo head) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head - 1 <= index && index < elementData.size
                    forAll<Int> { k -> (0 <= k && index - head < k && k < size) implies (element != elementAt(k)) }
                }
                if (element == elementData[index]) return index - head
            }
        }

        return -1
    }

    // D-8: Unannotated search separates bounds from the equality-dependent postconditions.
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed public fun <!VIPER_TEXT!>indexOfUntrusted<!>(element: E): Int {
        postconditions<Int> { result ->
            result >= -1 && result < size
            (result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == elementAt(i) }
            (result >= 0) implies (element == elementAt(result))
            forAll<Int> { i -> (0 <= i && i < result) implies (element != elementAt(i)) }
        }
        val tail = internalIndex(size)

        if (head < tail) {
            for (index in head until tail) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head <= index && index <= tail
                }
                if (element == elementData[index]) <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>return index - head<!>
            }
        } else if (isNotEmpty() && head >= tail) {
            for (index in head until elementData.size) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    head <= index && index <= elementData.size
                }
                if (element == elementData[index]) <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>return index - head<!>
            }
            for (index in 0 until tail) {
                loopInvariants {
                    size == old(size) && head == old(head) && elementData.size == old(elementData.size)
                    forAll<Int> { k -> (0 <= k && k < elementData.size) implies (elementData[k] === old(elementData[k])) }
                    0 <= index && index <= tail
                }
                if (element == elementData[index]) <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>return index + elementData.size - head<!>
            }
        }

        return -1
    }<!>

    @Unique @Borrowed public fun <!VIPER_TEXT!>clear<!>() {
        postconditions<Unit> {
            size == 0
            elementData.size == old(elementData.size)
            forAll<Int> { i -> triggers(old(internalIndex(i))); (0 <= i && i < old(size)) implies (elementData[old(internalIndex(i))] === null) }
        }
        if (isNotEmpty()) {
            registerModification()

            val tail = internalIndex(size)
            nullifyNonEmpty(head, tail)
        }
        head = 0
        // D-2: count stores the mutable size.
        count = 0
    }

    /** If `internalFromIndex == internalToIndex`, the buffer is considered full and all elements are nullified. */
    @Unique @Borrowed private fun <!VIPER_TEXT!>nullifyNonEmpty<!>(internalFromIndex: Int, internalToIndex: Int) {
        preconditions { 0 <= internalFromIndex && internalFromIndex < elementData.size && 0 <= internalToIndex && internalToIndex < elementData.size }
        postconditions<Unit> {
            size == old(size)
            head == old(head)
            elementData.size == old(elementData.size)
            forAll<Int> { i -> (0 <= i && i < elementData.size &&
                (if (internalFromIndex < internalToIndex) internalFromIndex <= i && i < internalToIndex else internalFromIndex <= i || i < internalToIndex)) implies (elementData[i] === null) }
        }
        if (internalFromIndex < internalToIndex) {
            elementData.fill(null, internalFromIndex, internalToIndex)
        } else {
            elementData.fill(null, internalFromIndex, elementData.size)
            elementData.fill(null, 0, internalToIndex)
        }
    }

    // D-8: P2 negative: the vacating write is absent from this removeFirst sibling.
    @Unique @Borrowed fun <!VIPER_TEXT!>removeKeepsSlot<!>(): E {
        postconditions<E> { elementData[old(head)] === null }
        if (isEmpty()) throw NoSuchElementException("ArrayDeque is empty.")
        registerModification()

        val element = internalGet(head)
        head = incremented(head)
        count -= 1
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return element<!>
    }

    // D-8: This addLast sibling omits the capacity precondition on its contract.
    @Unique @Borrowed fun <!VIPER_TEXT!>addLastWithoutCapacity<!>(element: E) {
        postconditions<Unit> { size == <!INTEGER_OVERFLOW!>old(size) + 1<!> }
        registerModification()
        ensureCapacity(size + 1)

        elementData[internalIndex(size)] = element
        count += 1
    }

    @SpecOf("get") @Unique @Borrowed fun getSpec(index: Int) {
        postconditions<E> { r ->
            size == old(size)
            elementData.size == old(elementData.size)
            (0 <= index && index < size) implies (r === elementAt(index))
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
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
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size && i != index) implies (elementAt(i) === old(elementAt(i))) }
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

// D-4: Private copies of AbstractList capacity and index helpers.
private const val defaultMinCapacity = 10
private const val maxArraySize = Int.MAX_VALUE - 8

@WrappingIntArithmetic
private fun <!VIPER_TEXT!>newCapacity<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> {
        it >= minCapacity
        (oldCapacity < minCapacity && minCapacity <= 715827882) implies (it <= 1073741824)
        (oldCapacity < minCapacity) implies (it > oldCapacity)
    }
    // overflow-conscious
    var newCapacity = oldCapacity + (oldCapacity shr 1)
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySize > 0)
        newCapacity = if (minCapacity > maxArraySize) Int.MAX_VALUE else maxArraySize
    return newCapacity
}

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

fun <E> <!VIPER_TEXT!>rotateLeft<!>(d: @Unique @Borrowed ArrayDeque<E>) {
    preconditions { 0 < d.size && d.size < 715827882 }
    postconditions<Unit> {
        d.size == old(d.size)
        (d.size > 0) implies (d.elementAt(d.size - 1) === old(d.elementAt(0)))
        forAll<Int> { i -> (0 <= i && i < d.size - 1) implies (d.elementAt(i) === old(d.elementAt(i + 1))) }
    }
    val first = d.removeFirst()
    d.addLast(first)
}

fun <E> <!VIPER_TEXT!>rotateRight<!>(d: @Unique @Borrowed ArrayDeque<E>) {
    preconditions { 0 < d.size && d.size < 715827882 }
    postconditions<Unit> {
        d.size == old(d.size)
        (d.size > 0) implies (d.elementAt(0) === old(d.elementAt(d.size - 1)))
        forAll<Int> { i -> (0 <= i && i < d.size - 1) implies (d.elementAt(i + 1) === old(d.elementAt(i))) }
    }
    val last = d.removeLast()
    d.addFirst(last)
}

fun <E> <!VIPER_TEXT!>rotateLeftUnchanged<!>(d: @Unique @Borrowed ArrayDeque<E>) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions { 1 < d.size && d.size < 715827882 }
    postconditions<Unit> {
        forAll<Int> { i -> (0 <= i && i < d.size && i < old(d.size)) implies (d.elementAt(i) === old(d.elementAt(i))) }
    }
    rotateLeft(d)
}<!>

fun <E> <!VIPER_TEXT!>rotateRightUnchanged<!>(d: @Unique @Borrowed ArrayDeque<E>) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions { d.size == 2 && d.elementAt(0) !== d.elementAt(1) }
    postconditions<Unit> {
        forAll<Int> { i -> (0 <= i && i < d.size && i < old(d.size)) implies (d.elementAt(i) === old(d.elementAt(i))) }
    }
    rotateRight(d)
}<!>

fun <E> <!VIPER_TEXT!>endOperations<!>(first: E, last: E) {
    val d: @Unique ArrayDeque<E> = ArrayDeque()
    d.addFirst(first)
    val added = d.add(last)
    verify(added && d.size == 2)
    val back = d.removeLastOrNull()
    val front = d.removeFirstOrNull()
    verify(back === last && front === first && d.size == 0)
    val emptyBack = d.removeLastOrNull()
    val emptyFront = d.removeFirstOrNull()
    verify(emptyBack === null && emptyFront === null)
}

// D-8: Bounds negative for the second disjoint copy in copyElements.
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>copyElementsPastEnd<!>(data: @Unique @Borrowed Array<Any?>, head: Int, newCapacity: Int) {
    preconditions { data.size > 0 && data.size < newCapacity && newCapacity <= 1073741824 && 0 <= head && head < data.size }
    val newElements = arrayOfNulls<Any?>(newCapacity)
    data.copyInto(newElements, 0, head, data.size)
    data.copyInto(newElements, data.size - head, 0, data.size + 1)
}<!>

// D-8: The AbstractList capacity body with checked arithmetic.
private fun <!VIPER_TEXT!>checkedCapacity<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> { it >= minCapacity }
    // overflow-conscious
    var newCapacity = <!INTEGER_OVERFLOW!>oldCapacity + (oldCapacity shr 1)<!>
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySize > 0)
        newCapacity = if (minCapacity > maxArraySize) Int.MAX_VALUE else maxArraySize
    return newCapacity
}

fun <E> <!VIPER_TEXT!>untrustedSearchBounds<!>(d: @Unique @Borrowed ArrayDeque<E>, element: E) {
    val index = d.indexOfUntrusted(element)
    verify(index >= -1 && index < d.size)
}

fun <!VIPER_TEXT!>searchAndClear<!>(d: @Unique @Borrowed ArrayDeque<Int>, element: Int) {
    val first = d.indexOf(element)
    val last = d.lastIndexOf(element)
    val present = d.contains(element)
    verify((first == -1) == !present && (last == -1) == !present)
    verify((first >= 0) implies (first <= last))
    d.clear()
    val after = d.contains(element)
    verify(d.size == 0 && !after)
}

fun <!VIPER_TEXT!>clearKeepsElement<!>(d: @Unique @Borrowed ArrayDeque<Int>, element: Int) {
    d.clear()
    val after = d.contains(element)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>after<!>)
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
