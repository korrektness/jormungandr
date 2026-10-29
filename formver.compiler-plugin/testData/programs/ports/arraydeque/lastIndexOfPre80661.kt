// FULL_JDK
@file:Suppress("NOTHING_TO_INLINE")
import org.jetbrains.kotlin.formver.plugin.*

interface DequeSpec<E> {
    @Pure @get:Unique val size: Int
    @Pure @Unique fun <!VIPER_TEXT!>elementAt<!>(index: Int): E
    @SpecOf("elementAt") @Unique fun elementAtSpec(index: Int) {
        preconditions { 0 <= index && index < size }
    }
}

// Search-state fixture; the body is compared mechanically with the port.
<!VIPER_TEXT!>class SearchDeque<!><E> : DequeSpec<E> {
    private var head = 0
    private var count = 0
    private var elementData: @Unique Array<Any?> = arrayOfNulls(0)
    @Pure @get:Unique override val size: Int <!VIPER_TEXT!>get<!>() = count
    @Law @Pure @Unique fun representation(): Boolean =
        0 <= count && count <= elementData.size &&
        (elementData.size == 0 || (0 <= head && head < elementData.size)) &&
        (elementData.size != 0 || head == 0) && elementData.size <= 1073741824

    @Pure @Unique private fun <!VIPER_TEXT!>isNotEmpty<!>(): Boolean = size != 0
    @SpecOf("positiveMod") @Unique private fun positiveModSpec(index: Int) {
        preconditions { 0 <= index && (if (elementData.size == 0) index == 0 else index - elementData.size < elementData.size) }
        postconditions<Int> { r ->
            0 <= r && (if (elementData.size == 0) r == 0 else r < elementData.size)
            r == (if (index >= elementData.size) index - elementData.size else index)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>positiveMod<!>(index: Int): Int = if (index >= elementData.size) index - elementData.size else index
    @SpecOf("internalIndex") @Unique private fun internalIndexSpec(index: Int) {
        preconditions { 0 <= index && index <= size && (elementData.size == 0 || head + index - elementData.size < elementData.size) }
        postconditions<Int> { r ->
            0 <= r && (if (elementData.size == 0) r == 0 else r < elementData.size)
            r == (if (head + index >= elementData.size) head + index - elementData.size else head + index)
        }
    }
    @Pure @Unique private inline fun <!VIPER_TEXT!>internalIndex<!>(index: Int): Int = positiveMod(head + index)
    @Pure @Unique override fun <!VIPER_TEXT!>elementAt<!>(index: Int): E {
        preconditions { 0 <= index && index < size }
        val physical = positiveMod(head + index)
        @Suppress("UNCHECKED_CAST")
        return elementData[physical] as E
    }

    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@TrustedElementEquality @Unique @Borrowed public fun <!VIPER_TEXT!>lastIndexOfPre80661<!>(element: E): Int {
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
        } else if (head > tail) {
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
    }<!>
}
