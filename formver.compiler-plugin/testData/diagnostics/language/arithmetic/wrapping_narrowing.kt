// FULL_JDK
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

@WrappingNarrowing
fun <!VIPER_TEXT!>truncations<!>() {
    verify(200.toByte() == (-56).toByte(), 255.toByte().toInt() == -1, 128.toByte().toInt() == -128)
    verify((-129).toByte().toInt() == 127, 127.toByte().toInt() == 127)
    verify(40000.toShort().toInt() == -25536)
    verify(4294967296L.toInt() == 0, 2147483648L.toInt() == -2147483648, (-1L).toInt() == -1)
    verify(Long.MIN_VALUE.toInt() == 0, Long.MAX_VALUE.toInt() == -1)
    verify((-1).toChar().code == 65535)
    refute(false)
}

// The low byte of an Int, as upstream code writes it before a ByteArray store.
@WrappingNarrowing
fun <!VIPER_TEXT!>storeLowByte<!>(out: @Unique @Borrowed ByteArray, v: Int) {
    preconditions { out.size > 0 && 0 <= v && v <= 65535 }
    postconditions<Unit> {
        (v % 256 <= 127) implies (out[0].toInt() == v % 256)
        (v % 256 >= 128) implies (out[0].toInt() == v % 256 - 256)
    }
    out[0] = v.toByte()
}

@WrappingNarrowing
fun <!VIPER_TEXT!>truncationIsInRange<!>(x: Long): Int {
    postconditions<Int> { r -> -128 <= r && r <= 127 }
    return x.toByte().toInt()
}

@WrappingNarrowing
fun <!VIPER_TEXT!>wrongTruncation<!>(x: Int) {
    preconditions { x == 200 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x.toByte().toInt() == 200<!>)
}

// Widening keeps its exact meaning.
@WrappingNarrowing
fun <!VIPER_TEXT!>wideningExact<!>(b: Byte): Long {
    postconditions<Long> { r -> r == b.toLong() && -128L <= r && r <= 127L }
    return b.toLong()
}

// Without the annotation, narrowing must prove the target range.
fun <!VIPER_TEXT!>checkedNarrowing<!>(x: Int): Byte = <!INTEGER_OVERFLOW!>x.toByte()<!>
