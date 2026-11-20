// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

/** Whether one of the first five bytes from [pos] ends an unsigned LEB128 varint: its high bit is clear. */
@Pure
fun <!VIPER_TEXT!>terminates<!>(bytes: @Unique @Borrowed ByteArray, pos: Int): Boolean {
    preconditions { 0 <= pos && pos <= bytes.size }
    return (bytes.size - pos > 0 && bytes[pos] >= 0) ||
        (bytes.size - pos > 1 && bytes[pos + 1] >= 0) ||
        (bytes.size - pos > 2 && bytes[pos + 2] >= 0) ||
        (bytes.size - pos > 3 && bytes[pos + 3] >= 0) ||
        (bytes.size - pos > 4 && bytes[pos + 4] >= 0)
}

fun <!VIPER_TEXT!>readVarint<!>(bytes: @Unique @Borrowed ByteArray, pos: Int): Long {
    preconditions { 0 <= pos && pos <= bytes.size }
    postconditions<Long> { terminates(bytes, pos) }
    throwsOnlyIf { !terminates(bytes, pos) }
    refute(false)
    var result = 0L
    var scale = 1L
    var i = 0
    while (i < 5 && bytes.size - pos > i) {
        loopInvariants {
            0 <= i && i <= 5 && i <= bytes.size - pos
            0L <= result && result < scale
            terminates(bytes, pos) == old(terminates(bytes, pos))
            (i == 0) implies (scale == 1L)
            (i == 1) implies (scale == 128L)
            (i == 2) implies (scale == 16384L)
            (i == 3) implies (scale == 2097152L)
            (i == 4) implies (scale == 268435456L)
            (i == 5) implies (scale == 34359738368L)
            (i > 0) implies (bytes[pos] < 0)
            (i > 1) implies (bytes[pos + 1] < 0)
            (i > 2) implies (bytes[pos + 2] < 0)
            (i > 3) implies (bytes[pos + 3] < 0)
            (i > 4) implies (bytes[pos + 4] < 0)
        }
        val b = bytes[pos + i]
        result += (if (b < 0) b + 128 else b.toInt()) * scale
        scale *= 128
        if (b >= 0) return result
        i++
        refute(false)
    }
    refute(false)
    throw IllegalArgumentException("malformed varint")
}

fun <!VIPER_TEXT!>readVarintTooEager<!>(bytes: @Unique @Borrowed ByteArray, pos: Int): Long {
    preconditions { 0 <= pos && pos <= bytes.size }
    throwsOnlyIf { !terminates(bytes, pos) }
    var result = 0L
    var scale = 1L
    var i = 0
    while (i < 4 && bytes.size - pos > i) {
        loopInvariants {
            0 <= i && i <= 4 && i <= bytes.size - pos
            0L <= result && result < scale
            terminates(bytes, pos) == old(terminates(bytes, pos))
            (i == 0) implies (scale == 1L)
            (i == 1) implies (scale == 128L)
            (i == 2) implies (scale == 16384L)
            (i == 3) implies (scale == 2097152L)
            (i == 4) implies (scale == 268435456L)
            (i > 0) implies (bytes[pos] < 0)
            (i > 1) implies (bytes[pos + 1] < 0)
            (i > 2) implies (bytes[pos + 2] < 0)
            (i > 3) implies (bytes[pos + 3] < 0)
        }
        val b = bytes[pos + i]
        result += (if (b < 0) b + 128 else b.toInt()) * scale
        scale *= 128
        if (b >= 0) return result
        i++
    }
    <!THROWS_CONDITION_NOT_ESTABLISHED!>throw <!THROWS_CONDITION_NOT_ESTABLISHED!>IllegalArgumentException("malformed varint")<!><!>
}
