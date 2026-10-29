// FULL_JDK
// RETURN_VALUE_CHECKER_MODE: CHECKER
@file:Suppress("RETURN_VALUE_NOT_USED")
import org.jetbrains.kotlin.formver.plugin.*

// A circular buffer whose observer elementAt reads owned array storage, as in the ArrayDeque port.
<!VIPER_TEXT!>class Ring<!><E> {
    private var head: Int = 0
    private var elementData: @Unique Array<Any?>
    private var count: Int = 0
    private var modCount: Int = 0
    @Pure @get:Unique val size: Int <!VIPER_TEXT!>get<!>() = count

    @Law @Pure @Unique fun representation(): Boolean =
        0 <= count && count <= elementData.size &&
        (elementData.size == 0 || (0 <= head && head < elementData.size)) &&
        (elementData.size != 0 || head == 0) && elementData.size <= 1073741824

    constructor() {
        elementData = arrayOfNulls(0)
    }

    @SpecOf("positiveMod") @Unique private fun positiveModSpec(index: Int) {
        preconditions { elementData.size > 0 && 0 <= index && index - elementData.size < elementData.size }
        postconditions<Int> { r ->
            0 <= r && r < elementData.size
            r == (if (index >= elementData.size) index - elementData.size else index)
        }
    }
    @Pure @Unique private fun <!VIPER_TEXT!>positiveMod<!>(index: Int): Int = if (index >= elementData.size) index - elementData.size else index

    @Pure @Unique fun <!VIPER_TEXT!>elementAt<!>(index: Int): E {
        preconditions { 0 <= index && index < size }
        val physical = positiveMod(head + index)
        @Suppress("UNCHECKED_CAST")
        return elementData[physical] as E
    }

    // The frame's derived trigger is the current elementAt(i) only.
    @Unique @Borrowed private fun <!VIPER_TEXT!>touch<!>() {
        postconditions<Unit> {
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        modCount = 0
    }

    // The same frame, also triggered by the entry value old(elementAt(i)).
    @Unique @Borrowed private fun <!VIPER_TEXT!>touchBoth<!>() {
        postconditions<Unit> {
            size == old(size)
            elementData.size == old(elementData.size)
            head == old(head)
            forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }
        }
        modCount = 0
    }

    // The two halves of the bridge below, each after a frame-only call.
    @Unique @Borrowed fun <!VIPER_TEXT!>observerAfterTouch<!>() {
        touch()
        verify(forAll<Int> { i -> (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) })
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>physicalCurrentAfterTouch<!>() {
        touch()
        verify(forAll<Int> { i -> (0 <= i && i < size && head + i < elementData.size) implies (elementData[head + i] === elementAt(i)) })
    }

    // A physical read against the entry observer, after a frame-only call.
    @Unique @Borrowed fun <!VIPER_TEXT!>physicalAfterTouch<!>() {
        touch()
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>forAll<Int> { i -> (0 <= i && i < size && head + i < elementData.size) implies (elementData[head + i] === old(elementAt(i))) }<!>)
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>physicalAfterTouchBoth<!>() {
        touchBoth()
        verify(forAll<Int> { i -> (0 <= i && i < size && head + i < elementData.size) implies (elementData[head + i] === old(elementAt(i))) })
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>pushNoCall<!>(element: E) {
        preconditions { size < elementData.size }
        postconditions<Unit> {
            size == old(size) + 1
            elementAt(size - 1) === element
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
        elementData[positiveMod(head + count)] = element
        count += 1
    }

    // The frame of touch is not instantiated: no term of the intermediate state names elementAt.
    @Unique @Borrowed fun <!VIPER_TEXT!>pushAfterTouch<!>(element: E) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
        preconditions { size < elementData.size }
        postconditions<Unit> {
            size == old(size) + 1
            elementAt(size - 1) === element
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
        touch()
        elementData[positiveMod(head + count)] = element
        count += 1
    }<!>

    @Unique @Borrowed fun <!VIPER_TEXT!>pushAfterTouchBoth<!>(element: E) {
        preconditions { size < elementData.size }
        postconditions<Unit> {
            size == old(size) + 1
            elementAt(size - 1) === element
            forAll<Int> { i -> (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
        touchBoth()
        touchBoth()
        elementData[positiveMod(head + count)] = element
        count += 1
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>pushFrontAfterTouchBoth<!>(element: E) {
        preconditions { size < elementData.size }
        postconditions<Unit> {
            size == old(size) + 1
            elementAt(0) === element
            forAll<Int> { i -> triggers(old(elementAt(i))); (0 <= i && i < old(size)) implies (elementAt(i + 1) === old(elementAt(i))) }
        }
        touchBoth()
        touchBoth()
        head = if (head == 0) elementData.size - 1 else head - 1
        elementData[head] = element
        count += 1
    }

    // The frame claims the unshifted offsets after a front insertion.
    @Unique @Borrowed fun <!VIPER_TEXT!>pushFrontWrongFrame<!>(element: E) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
        preconditions { size < elementData.size && size > 0 }
        postconditions<Unit> {
            size == old(size) + 1
            forAll<Int> { i -> triggers(old(elementAt(i))); (0 <= i && i < old(size)) implies (elementAt(i) === old(elementAt(i))) }
        }
        touchBoth()
        head = if (head == 0) elementData.size - 1 else head - 1
        elementData[head] = element
        count += 1
    }<!>
}
