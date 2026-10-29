// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// A `@Pure` member call of the receiver indexing a unique array field in a contract.

<!VIPER_TEXT!>class ReproGet<!>(var elementData: @Unique Array<Any?>, val head: Int) {
    @Pure @Unique @Borrowed fun <!VIPER_TEXT!>positiveMod<!>(i: Int): Int {
        preconditions { elementData.size > 0 && i >= 0 && i < elementData.size }
        return i
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>get<!>(index: Int): Any? {
        preconditions { head == 0 && index >= 0 && index < elementData.size }
        postconditions<Any?> { result ->
            elementData.size == old(elementData.size)
            result === elementData[positiveMod(head + index)]
        }
        refute(false)
        return elementData[positiveMod(head + index)]
    }

    // The callee's precondition fails for the last index.
    @Unique @Borrowed fun <!VIPER_TEXT!>getPastEnd<!>(index: Int): Any? {
        preconditions { head == 0 && index >= 0 && index < elementData.size }
        postconditions<Any?> { result ->
            elementData.size == old(elementData.size)
            elementData[<!VIPER_VERIFICATION_ERROR!>positiveMod(head + index + 1)<!>] === elementData[positiveMod(head + index + 1)]
        }
        return elementData[positiveMod(head + index)]
    }
}

<!VIPER_TEXT!>class ReproCopy<!>(var elementData: @Unique Array<Any?>, val head: Int) {
    @Pure @Unique @Borrowed fun <!VIPER_TEXT!>positiveMod<!>(i: Int): Int {
        preconditions { elementData.size > 0 && i >= 0 && i < elementData.size }
        return i
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>copy<!>() {
        preconditions { head == 0 && elementData.size > 0 }
        postconditions<Unit> {
            elementData.size == old(elementData.size)
            forAll<Int> { i -> (0 <= i && i < elementData.size) implies
                (elementData[positiveMod(head + i)] === old(elementData[positiveMod(head + i)])) }
        }
        refute(false)
        val fresh = arrayOfNulls<Any?>(elementData.size)
        elementData.copyInto(fresh, 0, 0, elementData.size)
        elementData = fresh
    }

    // Copies shifted by one slot, so the frame claim is false.
    @Unique @Borrowed fun <!VIPER_TEXT!>copyShifted<!>() {
        preconditions { head == 0 && elementData.size > 0 }
        postconditions<Unit> {
            elementData.size == old(elementData.size)
            <!VIPER_VERIFICATION_ERROR!>forAll<Int> { i -> (0 <= i && i < elementData.size) implies
                (elementData[positiveMod(head + i)] === old(elementData[positiveMod(head + i)])) }<!>
        }
        val fresh = arrayOfNulls<Any?>(elementData.size)
        elementData.copyInto(fresh, 1, 0, elementData.size - 1)
        elementData = fresh
    }
}

// `positiveMod` depends on the size of the array that `grow` replaces, so each call is evaluated in its own heap.
<!VIPER_TEXT!>class Growing<!>(var elementData: @Unique Array<Any?>) {
    @Pure @Unique @Borrowed fun <!VIPER_TEXT!>positiveMod<!>(i: Int): Int {
        preconditions { elementData.size > 0 && i >= 0 && i - elementData.size < elementData.size }
        return if (i >= elementData.size) i - elementData.size else i
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>grow<!>() {
        preconditions { elementData.size > 0 && elementData.size < 1000 }
        postconditions<Unit> {
            elementData.size == old(elementData.size) * 2
            elementData[0] === old(elementData[positiveMod(elementData.size)])
            elementData[positiveMod(old(elementData.size))] === null
        }
        refute(false)
        val fresh = arrayOfNulls<Any?>(elementData.size * 2)
        elementData.copyInto(fresh, 0, 0, elementData.size)
        elementData = fresh
    }

    // Holds only if `positiveMod` were evaluated against the array before `grow`.
    @Unique @Borrowed fun <!VIPER_TEXT!>growWrongHeap<!>() <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
        preconditions { elementData.size > 0 && elementData.size < 1000 }
        postconditions<Unit> {
            elementData.size == old(elementData.size) * 2
            elementData[positiveMod(old(elementData.size))] === old(elementData[0])
        }
        val fresh = arrayOfNulls<Any?>(elementData.size * 2)
        elementData.copyInto(fresh, 0, 0, elementData.size)
        elementData = fresh
    }<!>
}
