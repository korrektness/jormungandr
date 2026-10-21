// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Element<!>

fun <!VIPER_TEXT!>copyGrow<!>(src: @Unique @Borrowed Array<Element?>, n: Int, split: Int): @Unique Array<Element?> {
    preconditions { n >= src.size; 0 <= split && split <= src.size }
    postconditions<Array<Element?>> { result ->
        result.size == n
        forAll<Int> { i -> (0 <= i && i < src.size - split) implies (result[i] === old(src[i + split])) }
        forAll<Int> { i -> (src.size - split <= i && i < src.size) implies (result[i] === old(src[i - (src.size - split)])) }
        forAll<Int> { i -> (src.size <= i && i < n) implies (result[i] == null) }
        forAll<Int> { i -> (0 <= i && i < src.size) implies (src[i] === old(src[i])) }
    }
    val dst: @Unique Array<Element?> = arrayOfNulls<Element>(n)
    src.copyInto(dst, 0, split, src.size)
    src.copyInto(dst, src.size - split, 0, split)
    return dst
}

fun <!VIPER_TEXT!>fillWindow<!>(arr: @Unique @Borrowed Array<Element?>, value: Element?, from: Int, to: Int) {
    preconditions { 0 <= from && from <= to && to <= arr.size }
    postconditions<Unit> {
        forAll<Int> { i -> (from <= i && i < to) implies (arr[i] === value) }
        forAll<Int> { i -> (0 <= i && i < arr.size && (i < from || to <= i)) implies (arr[i] === old(arr[i])) }
    }
    arr.fill(value, from, to)
}

fun <!VIPER_TEXT!>copyDefaults<!>(src: @Unique @Borrowed Array<Element?>) {
    val dst: @Unique Array<Element?> = arrayOfNulls<Element>(src.size)
    val result: @Unique Array<Element?> = src.copyInto(dst)
    verify(forAll<Int> { i -> (0 <= i && i < src.size) implies (result[i] === src[i]) })
    result.fill(null)
    verify(forAll<Int> { i -> (0 <= i && i < result.size) implies (result[i] == null) })
}

fun <!VIPER_TEXT!>nestedCopy<!>(first: @Unique @Borrowed Array<Element?>, second: @Unique @Borrowed Array<Element?>) {
    preconditions { first.size == second.size }
    val dst: @Unique Array<Element?> = arrayOfNulls<Element>(first.size)
    val result: @Unique Array<Element?> = first.copyInto(second.copyInto(dst))
    verify(forAll<Int> { i -> (0 <= i && i < first.size) implies (result[i] === first[i]) })
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>copyPastEnd<!>(src: @Unique @Borrowed Array<Element?>, dst: @Unique @Borrowed Array<Element?>) {
    preconditions { src.size > 0; dst.size == 0 }
    src.copyInto(dst)
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>fillPastEnd<!>(arr: @Unique @Borrowed Array<Element?>) {
    arr.fill(null, -1, 0)
}<!>

fun <!VIPER_TEXT!>wrongFill<!>(arr: @Unique @Borrowed Array<Element?>) {
    preconditions { arr.size > 0 }
    arr.fill(null)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>arr[0] != null<!>)
}

fun <!VERIFICATION_SKIPPED!>selfCopy<!>(arr: @Unique @Borrowed Array<Element?>) {
    <!UNSUPPORTED_FEATURE!>arr.copyInto(arr)<!>
}
