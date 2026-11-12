// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// `Byte.toInt()` sign-extends: a stored byte keeps its signed value.
fun <!VIPER_TEXT!>unsignedByteAt<!>(bytes: @Unique @Borrowed ByteArray, i: Int): Int {
    preconditions { 0 <= i && i < bytes.size }
    postconditions<Int> { r ->
        0 <= r && r <= 255
        (bytes[i] < 0) implies (r == bytes[i].toInt() + 256)
        (bytes[i] >= 0) implies (r == bytes[i].toLong().toInt())
    }
    val b = bytes[i].toInt()
    return if (b < 0) b + 256 else b
}

fun <!VIPER_TEXT!>storeByte<!>(bytes: @Unique @Borrowed ByteArray, i: Int, v: Byte) {
    preconditions { 0 <= i && i < bytes.size }
    postconditions<Unit> { bytes[i] == v && bytes.size == old(bytes.size) }
    bytes[i] = v
}

fun <!VIPER_TEXT!>byteRange<!>(bytes: ByteArray, i: Int): Int {
    preconditions { 0 <= i && i < bytes.size }
    postconditions<Int> { r -> -128 <= r && r <= 127 }
    return bytes[i].toInt()
}

fun <!VIPER_TEXT!>zeroBytes<!>(n: Int) {
    preconditions { n > 0 }
    val bytes: @Unique ByteArray = ByteArray(n)
    verify(bytes.size == n, bytes[n - 1].toInt() == 0)
}

fun <!VIPER_TEXT!>countNegative<!>(bytes: @Unique @Borrowed ByteArray): Int {
    postconditions<Int> { r -> 0 <= r && r <= bytes.size }
    var count = 0
    var i = 0
    while (i < bytes.size) {
        loopInvariants { 0 <= i && i <= bytes.size && 0 <= count && count <= i }
        if (bytes[i] < 0) count++
        i++
    }
    return count
}

fun <!VIPER_TEXT!>byteLoops<!>(n: Int) {
    preconditions { 0 < n && n <= 100 }
    val bytes: @Unique ByteArray = ByteArray(n) { (it - 50).toByte() }
    verify(bytes[0].toInt() == -50)
    if (bytes.all { it >= -50 }) {
        verify(bytes[n - 1] >= -50)
    }
    val r = bytes.indexOfFirst { it < 0 }
    if (r >= 0) {
        verify(bytes[r] < 0)
    }
    var sum = 0
    bytes.forEach {
        loopInvariants { -1000000 <= sum && sum <= 1000000 }
        if (-1000000 + 128 <= sum && sum <= 1000000 - 127) sum += it
    }
}

fun <!VIPER_TEXT!>byteOverflow<!>(bytes: @Unique @Borrowed ByteArray) {
    preconditions { bytes.size > 0 }
    <!INTEGER_OVERFLOW!>bytes[0]++<!>
}

fun <!VIPER_TEXT!>byteOutOfBounds<!>(bytes: @Unique @Borrowed ByteArray): Byte = <!POSSIBLE_INDEX_OUT_OF_BOUND!>bytes[bytes.size]<!>

fun <!VIPER_TEXT!>charCodes<!>(n: Int): @Unique CharArray {
    preconditions { 0 <= n && n <= 26 }
    postconditions<CharArray> { r ->
        r.size == n
        forAll<Int> { k -> (0 <= k && k < n) implies (r[k].code == 97 + k) }
    }
    return CharArray(n) { 'a' + it }
}

fun <!VIPER_TEXT!>isDigitAt<!>(chars: @Unique @Borrowed CharArray, i: Int): Boolean {
    preconditions { 0 <= i && i < chars.size }
    postconditions<Boolean> { r -> r == ('0' <= chars[i] && chars[i] <= '9') }
    val c = chars[i]
    return '0' <= c && c <= '9'
}

fun <!VIPER_TEXT!>defaultChar<!>(n: Int) {
    preconditions { n > 0 }
    val chars: @Unique CharArray = CharArray(n)
    verify(chars[0].code == 0)
}

fun <!VIPER_TEXT!>longElement<!>(longs: @Unique @Borrowed LongArray, i: Int) {
    preconditions { 0 <= i && i < longs.size }
    postconditions<Unit> { longs[i] == 4294967296L }
    longs[i] = 4294967296L
}

fun <!VIPER_TEXT!>longToInt<!>(longs: @Unique @Borrowed LongArray): Int {
    preconditions { longs.size > 0 }
    return <!INTEGER_OVERFLOW!>longs[0].toInt()<!>
}

fun <!VIPER_TEXT!>sharedLong<!>(longs: LongArray): Long {
    preconditions { longs.size > 0 }
    postconditions<Long> { r -> r >= Long.MIN_VALUE }
    longs[0] = 1L
    return longs[0]
}
