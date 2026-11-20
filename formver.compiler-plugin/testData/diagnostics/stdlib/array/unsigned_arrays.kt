// FULL_JDK
@file:OptIn(ExperimentalUnsignedTypes::class)

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>elementRanges<!>(bytes: @Unique @Borrowed UByteArray, shorts: @Unique @Borrowed UShortArray, ints: @Unique @Borrowed UIntArray, longs: @Unique @Borrowed ULongArray, i: Int) {
    preconditions { 0 <= i && i < bytes.size && i < shorts.size && i < ints.size && i < longs.size }
    verify(bytes[i].toInt() <= 255, shorts[i].toInt() <= 65535, ints[i].toLong() <= 4294967295L, longs[i] <= ULong.MAX_VALUE)
}

fun <!VIPER_TEXT!>elementNotSigned<!>(ints: @Unique @Borrowed UIntArray, i: Int): Int {
    preconditions { 0 <= i && i < ints.size }
    return <!INTEGER_OVERFLOW!>ints[i].toInt()<!>
}

fun <!VIPER_TEXT!>swap<!>(arr: @Unique @Borrowed UIntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> { arr[i] == old(arr[j]) && arr[j] == old(arr[i]) }
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

fun <!VIPER_TEXT!>copy<!>(a: @Unique @Borrowed UIntArray, i: Int, j: Int) {
    preconditions { 0 <= i && i < a.size && 0 <= j && j < a.size }
    a[i] = a[j]
}

fun <!VIPER_TEXT!>outOfBounds<!>(arr: @Unique @Borrowed ULongArray, i: Int): ULong {
    preconditions { 0 <= i && i <= arr.size }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[i]<!>
}

fun <!VIPER_TEXT!>incrementElement<!>(arr: @Unique @Borrowed UByteArray) {
    preconditions { arr.size > 0 && arr[0] < UByte.MAX_VALUE }
    postconditions<Unit> { arr[0].toInt() == old(arr[0]).toInt() + 1 }
    arr[0]++
}

fun <!VIPER_TEXT!>incrementOverflow<!>(arr: @Unique @Borrowed UByteArray) {
    preconditions { arr.size > 0 }
    <!INTEGER_OVERFLOW!>arr[0]++<!>
}

fun <!VIPER_TEXT!>zeros<!>(n: Int) {
    preconditions { n > 0 }
    val arr: @Unique UIntArray = UIntArray(n)
    verify(arr.size == n, arr[n - 1] == 0u)
}

fun <!VIPER_TEXT!>sumIndices<!>(arr: @Unique @Borrowed UShortArray): ULong {
    var sum = 0uL
    for (i in arr.indices) {
        loopInvariants { sum <= i.toULong() * 65535uL }
        sum += arr[i]
    }
    return sum
}

fun <!VIPER_TEXT!>allZero<!>(n: Int): Boolean {
    preconditions { n > 0 }
    val a: @Unique UIntArray = UIntArray(n)
    return a.all { it == 0u }
}

fun <!VIPER_TEXT!>initialized<!>(n: Int): UIntArray {
    preconditions { n > 0 }
    return <!OPAQUE_CALL!>UIntArray(n) { <!INTEGER_OVERFLOW!>it.toUInt()<!> }<!>
}
