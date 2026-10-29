// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>longAddOverflow<!>(x: Long): Long {
    preconditions { x == 9223372036854775807L }
    return <!INTEGER_OVERFLOW!>x + 1L<!>
}
fun <!VIPER_TEXT!>longSubOverflow<!>(x: Long): Long {
    preconditions { x == -9223372036854775807L - 1L }
    return <!INTEGER_OVERFLOW!>x - 1L<!>
}
fun <!VIPER_TEXT!>longMulOverflow<!>(x: Long): Long {
    preconditions { x == 4611686018427387904L }
    return <!INTEGER_OVERFLOW!>x * 2L<!>
}
fun <!VIPER_TEXT!>longNegOverflow<!>(x: Long): Long {
    preconditions { x == -9223372036854775807L - 1L }
    return <!INTEGER_OVERFLOW!>-x<!>
}
fun <!VIPER_TEXT!>longDivOverflow<!>(x: Long): Long {
    preconditions { x == -9223372036854775807L - 1L }
    return <!INTEGER_OVERFLOW!>x / -1L<!>
}
fun <!VIPER_TEXT!>longRemMin<!>(x: Long) {
    preconditions { x == -9223372036854775807L - 1L }
    verify(x % -1L == 0L)
}
fun <!VIPER_TEXT!>longMinimumMagnitudes<!>(x: Long) {
    preconditions { x == -9223372036854775807L - 1L }
    verify(x / 2L == -4611686018427387904L, x % 2L == 0L)
    verify(x / x == 1L, x % x == 0L)
    verify(1L / x == 0L, 1L % x == 1L)
}
fun <!VIPER_TEXT!>longSigned<!>(x: Long, y: Long) {
    preconditions { x == -3L; y == 2L }
    verify(x / y == -1L, x % y == -1L, x / -y == 1L, x % -y == -1L)
}
fun <!VIPER_TEXT!>longIncOverflow<!>(x: Long): Long {
    preconditions { x == 9223372036854775807L }
    var y = x
    y<!INTEGER_OVERFLOW!>++<!>
    return y
}
fun <!VIPER_TEXT!>longDecOverflow<!>(x: Long): Long {
    preconditions { x == -9223372036854775807L - 1L }
    var y = x
    y<!INTEGER_OVERFLOW!>--<!>
    return y
}
fun <!VIPER_TEXT!>longZeroDiv<!>(x: Long): Long = <!DIVISION_BY_ZERO, DIVISION_BY_ZERO!>x / 0L<!>
fun <!VIPER_TEXT!>longZeroRem<!>(x: Long): Long = <!DIVISION_BY_ZERO!>x % 0L<!>
fun <!VIPER_TEXT!>byteZeroDiv<!>(x: Byte, y: Byte): Int {
    preconditions { y == 0.toByte() }
    return <!DIVISION_BY_ZERO!>x / y<!>
}
fun <!VIPER_TEXT!>byteZeroRem<!>(x: Byte, y: Byte): Int {
    preconditions { y == 0.toByte() }
    return <!DIVISION_BY_ZERO!>x % y<!>
}
fun <!VIPER_TEXT!>shortZeroDiv<!>(x: Short, y: Short): Int {
    preconditions { y == 0.toShort() }
    return <!DIVISION_BY_ZERO!>x / y<!>
}
fun <!VIPER_TEXT!>shortZeroRem<!>(x: Short, y: Short): Int {
    preconditions { y == 0.toShort() }
    return <!DIVISION_BY_ZERO!>x % y<!>
}
// Checked-width semantics are defined in SPECIFICATIONS.md, Integer arithmetic.
fun <!VIPER_TEXT!>byteInc<!>(x: Byte): Byte {
    preconditions { x == 127.toByte() }
    return <!INTEGER_OVERFLOW!>x.inc()<!>
}
fun <!VIPER_TEXT!>byteDec<!>(x: Byte): Byte {
    preconditions { x == (-128).toByte() }
    return <!INTEGER_OVERFLOW!>x.dec()<!>
}
fun <!VIPER_TEXT!>shortInc<!>(x: Short): Short {
    preconditions { x == 32767.toShort() }
    return <!INTEGER_OVERFLOW!>x.inc()<!>
}
fun <!VIPER_TEXT!>shortDec<!>(x: Short): Short {
    preconditions { x == (-32768).toShort() }
    return <!INTEGER_OVERFLOW!>x.dec()<!>
}
fun <!VIPER_TEXT!>bytePromotions<!>(x: Byte, y: Byte) {
    preconditions { x == (-128).toByte(); y == (-1).toByte() }
    verify(x + y == -129, x - y == -127, x * y == 128, -x == 128, +x == -128, x / y == 128, x % y == 0)
}
fun <!VIPER_TEXT!>shortPromotions<!>(x: Short, y: Short) {
    preconditions { x == (-32768).toShort(); y == (-1).toShort() }
    verify(x + y == -32769, x - y == -32767, x * y == 32768, -x == 32768, +x == -32768, x / y == 32768, x % y == 0)
}
fun <!VIPER_TEXT!>narrowByte<!>(x: Int): Byte {
    preconditions { x == 128 }
    return <!INTEGER_OVERFLOW!>x.toByte()<!>
}
fun <!VIPER_TEXT!>narrowShort<!>(x: Int): Short {
    preconditions { x == 32768 }
    return <!INTEGER_OVERFLOW!>x.toShort()<!>
}
fun <!VIPER_TEXT!>narrowInt<!>(x: Long): Int {
    preconditions { x == 2147483648L }
    return <!INTEGER_OVERFLOW!>x.toInt()<!>
}
fun <!VIPER_TEXT!>narrowChar<!>(x: Int): Char {
    preconditions { x == -1 }
    return <!INTEGER_OVERFLOW!>x.toChar()<!>
}
fun <!VIPER_TEXT!>exactWidening<!>(x: Byte, y: Short, z: Int, c: Char) {
    verify(x.toLong() == x.toInt().toLong(), y.toLong() == y.toInt().toLong(), z.toLong().toInt() == z)
    verify(c.<!DEPRECATION!>toInt<!>() >= 0, c.<!DEPRECATION!>toInt<!>() <= 65535)
}
fun <!VIPER_TEXT!>mixedWidths<!>(x: Byte, y: Short, z: Int, w: Long) {
    preconditions { x == 1.toByte(); y == 2.toShort(); z == 3; w == 4L }
    verify(x + y == 3, y + z == 5, z + w == 7L, w + x == 5L)
}
fun <!VIPER_TEXT!>charInc<!>(c: Char): Char {
    preconditions { c == '\uffff' }
    return <!INTEGER_OVERFLOW!>c.inc()<!>
}
fun <!VIPER_TEXT!>charDec<!>(c: Char): Char {
    preconditions { c == '\u0000' }
    return <!INTEGER_OVERFLOW!>c.dec()<!>
}
fun <!VIPER_TEXT!>compareWidths<!>(b: Byte, s: Short, l: Long, c: Char) {
    preconditions { b == 3.toByte(); s == 3.toShort(); l == 3L; c == 'c' }
    verify(b.compareTo(1.toByte()) == 1, s.compareTo(1.toShort()) == 1, l.compareTo(1L) == 1, c.compareTo('a') == 1)
}
fun <!VIPER_TEXT!>charCompareDifference<!>(c: Char) {
    preconditions { c == 'c' }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.compareTo('a') == 2<!>)
}
fun <!VIPER_TEXT!>longConstantNormalization<!>() {
    verify((-9223372036854775807L - 1L) < 0L, 9223372036854775807L > 0L)
}
