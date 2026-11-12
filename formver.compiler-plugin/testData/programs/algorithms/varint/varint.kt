// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// Protobuf's unsigned varint, ZigZag and fixed-width encodings, spelled as in kotlinx.serialization.

fun <!VIPER_TEXT!>encodeVarint<!>(value: Int, out: @Unique @Borrowed IntArray): Int {
    preconditions { out.size >= 5 }
    postconditions<Int> { n ->
        1 <= n && n <= 5
        0 <= out[n - 1] && out[n - 1] <= 127
        forAll<Int> { k -> (0 <= k && k < n - 1) implies (128 <= out[k] && out[k] <= 255) }
    }
    var v = value
    var i = 0
    while (v and 0x7F.inv() != 0) {
        loopInvariants {
            0 <= i && i <= 4
            v == value ushr (7 * i)
            forAll<Int> { k -> (0 <= k && k < i) implies (128 <= out[k] && out[k] <= 255) }
        }
        out[i] = (v and 0x7F) or 0x80
        v = v ushr 7
        i++
    }
    out[i] = v
    return i + 1
}

@Pure fun <!VIPER_TEXT!>varintGroup<!>(value: Int, index: Int): Int {
    preconditions { 0 <= index && index <= 4 }
    return (value ushr (7 * index)) and 0x7F
}

fun <!VIPER_TEXT!>decodeStep<!>(result: Int, b: Int, shift: Int): Int {
    preconditions {
        0 <= b && b <= 255
        shift == 0 || shift == 7 || shift == 14 || shift == 21
        0 <= result && result ushr shift == 0
    }
    postconditions<Int> { 0 <= it && it ushr (shift + 7) == 0 && it and ((1 shl shift) - 1) == result }
    return result or ((b and 0x7F) shl shift)
}

@Pure fun <!VIPER_TEXT!>zigZagEncode<!>(n: Int): Int = (n shl 1) xor (n shr 31)

@Pure fun <!VIPER_TEXT!>zigZagDecode<!>(n: Int): Int = (n ushr 1) xor -(n and 1)

@Pure fun <!VIPER_TEXT!>zigZagEncodeLong<!>(n: Long): Long = (n shl 1) xor (n shr 63)

@Pure fun <!VIPER_TEXT!>zigZagDecodeLong<!>(n: Long): Long = (n ushr 1) xor -(n and 1L)

fun <!VIPER_TEXT!>zigZagRoundTrip<!>(n: Int, m: Long) {
    verify(zigZagDecode(zigZagEncode(n)) == n, zigZagDecodeLong(zigZagEncodeLong(m)) == m)
    verify((n >= 0) implies (zigZagEncode(n) ushr 1 == n && zigZagEncode(n) and 1 == 0))
    verify((n < 0) implies (zigZagEncode(n) and 1 == 1))
}

fun <!VIPER_TEXT!>zigZagSmallValues<!>(n: Int) {
    preconditions { -1073741823 <= n && n <= 1073741823 }
    verify(zigZagEncode(n) == if (n >= 0) 2 * n else -2 * n - 1)
}

fun <!VIPER_TEXT!>readIntLittleEndian<!>(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
    postconditions<Int> {
        it and 0xFF == b0.toInt() and 0xFF
        (it shr 8) and 0xFF == b1.toInt() and 0xFF
        (it shr 16) and 0xFF == b2.toInt() and 0xFF
        it shr 24 == b3.toInt()
    }
    return (b0.toInt() and 0xFF) or ((b1.toInt() and 0xFF) shl 8) or ((b2.toInt() and 0xFF) shl 16) or
            ((b3.toInt() and 0xFF) shl 24)
}

fun <!VIPER_TEXT!>wrongContinuationBit<!>(value: Int): Int {
    postconditions<Int> { it <= 127 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return (value and 0x7F) or 0x80<!>
}
