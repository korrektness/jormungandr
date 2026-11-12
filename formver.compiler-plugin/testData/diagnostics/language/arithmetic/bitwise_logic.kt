// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>constants<!>() {
    verify(0x0F and 0x3C == 0x0C, 0x0F or 0xF0 == 0xFF, 0x0F xor 0xFF == 0xF0, 5.inv() == -6)
    verify(Int.MIN_VALUE xor -1 == Int.MAX_VALUE, Long.MIN_VALUE and Long.MAX_VALUE == 0L, 0L.inv() == -1L)
}

fun <!VIPER_TEXT!>literalMasks<!>(x: Int) {
    verify(0 <= (x and 0x7F) && (x and 0x7F) <= 127, (x and 0xFF00) % 256 == 0, (x and 0xFF00) >= 0)
    verify(x and 0 == 0, x and -1 == x, x or 0 == x, x or -1 == -1, x xor 0 == x, x xor -1 == x.inv())
    verify((x and -256) + (x and 0xFF) == x, (x and 0xFF) + (x and 0xFF.inv()) == x)
    verify(128 <= ((x and 0x7F) or 0x80) && ((x and 0x7F) or 0x80) <= 255, (x or 0x80) and 0x80 == 0x80)
    verify((x < 0) implies (x and Int.MIN_VALUE == Int.MIN_VALUE), (x >= 0) implies (x and Int.MIN_VALUE == 0))
    verify(x.inv() + x == -1)
}

fun <!VIPER_TEXT!>wrongMask<!>(x: Int) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>(x and 0x7F) < 127<!>)
}

fun <!VIPER_TEXT!>unsignedByte<!>(b: Byte) {
    verify(0 <= (b.toInt() and 0xFF) && (b.toInt() and 0xFF) <= 255)
    verify((b < 0) implies ((b.toInt() and 0xFF) == b + 256))
}

fun <!VIPER_TEXT!>zeroAndMinusOne<!>(a: Int, b: Int) {
    if (b == -1) verify(a and b == a, a or b == -1, a xor b == a.inv())
    if (a == 0) verify(a and b == 0, a or b == b, a xor b == b)
}

fun <!VIPER_TEXT!>disjointByDistance<!>(lo: Int, hi: Int, d: Int) {
    preconditions { 0 <= lo && lo < 256 && 8 <= d && d < 32 }
    verify(lo or (hi shl d) == lo + (hi shl d))
}

fun <!VIPER_TEXT!>variableMask<!>(x: Int, s: Int) {
    preconditions { 0 <= s && s < 31 }
    verify(0 <= (x and ((1 shl s) - 1)) && (x and ((1 shl s) - 1)) < (1 shl s))
}

fun <!VIPER_TEXT!>wrongSymbolic<!>(a: Int, b: Int) {
    preconditions { a >= 0 && b >= 0 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>(a and b) < a<!>)
}

fun <!VIPER_TEXT!>longMasks<!>(v: Long, w: Long) {
    verify(0L <= (v and 0x7FL) && (v and 0x7FL) <= 127L, (v xor -1L) == v.inv(), (v or 0x80L) and 0x80L == 0x80L)
    verify((v < 0L) implies (v and Long.MIN_VALUE == Long.MIN_VALUE))
    if (w == 0L) verify(v or w == v)
}

@Pure fun <!VIPER_TEXT!>lowByte<!>(x: Int): Int {
    postconditions<Int> { 0 <= it && it <= 255 && it == x and 0xFF }
    return x and 255
}

fun <!VIPER_TEXT!>maskInSpecification<!>(x: Int): Int {
    preconditions { x and 1 == 0 }
    postconditions<Int> { it and 1 == 1 }
    return x or 1
}
