// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>literalsAndComparisons<!>() {
    verify(1u < 2u, 0xFFu.toUByte() > 1u.toUByte(), UInt.MAX_VALUE == 4294967295u)
    verify(ULong.MAX_VALUE == 18446744073709551615uL, ULong.MAX_VALUE > 9223372036854775807uL)
    verify(UByte.MAX_VALUE.toInt() == 255, UShort.MAX_VALUE.toInt() == 65535)
}

fun <!VIPER_TEXT!>exactDivision<!>(a: UInt, b: UInt): UInt {
    preconditions { b > 0u }
    postconditions<UInt> { r -> (b == 1u) implies (r == a) }
    verify(7u / 2u == 3u, 7u % 2u == 1u, 4294967295u / 4294967295u == 1u)
    return a / b
}

fun <!VIPER_TEXT!>largeDivision<!>() {
    verify(ULong.MAX_VALUE / 2uL == 9223372036854775807uL, ULong.MAX_VALUE % 10uL == 5uL)
}

fun <!VIPER_TEXT!>divisionByZero<!>(a: UInt, b: UInt): UInt = <!DIVISION_BY_ZERO!>a / b<!>

fun <!VIPER_TEXT!>checkedPlus<!>(a: UInt, b: UInt): UInt {
    preconditions { a <= 100u && b <= 100u }
    return a + b
}

fun <!VIPER_TEXT!>plusOverflow<!>(a: UInt): UInt = <!INTEGER_OVERFLOW!>a + 1u<!>

fun <!VIPER_TEXT!>minusUnderflow<!>(a: UInt, b: UInt): UInt = <!INTEGER_OVERFLOW!>a - b<!>

fun <!VIPER_TEXT!>promotedTimes<!>(a: UByte, b: UByte): UInt {
    postconditions<UInt> { r -> r <= 65025u }
    return a * b
}

fun <!VIPER_TEXT!>promotedTimesBound<!>(a: UByte, b: UByte): UInt {
    postconditions<UInt> { r -> r <= 255u }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return a * b<!>
}

fun <!VIPER_TEXT!>ulongTimesOverflow<!>(a: ULong): ULong = <!INTEGER_OVERFLOW!>a * 2uL<!>

fun <!VIPER_TEXT!>incrementInRange<!>(a: UShort): UShort {
    preconditions { a < UShort.MAX_VALUE }
    var b = a
    b++
    return b
}

fun <!VIPER_TEXT!>decrementUnderflow<!>(a: UByte): UByte {
    var b = a
    b<!INTEGER_OVERFLOW!>--<!>
    return b
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>wrappingMinus<!>(a: UInt): UInt {
    postconditions<UInt> { r -> (a == 0u) implies (r == UInt.MAX_VALUE) }
    verify(UInt.MAX_VALUE * 2u == UInt.MAX_VALUE - 1u)
    return a - 1u
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>wrongUIntWrap<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>0u - 1u == 2147483647u<!>)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>wrappingTimes<!>() {
    verify(ULong.MAX_VALUE * ULong.MAX_VALUE == 1uL, ULong.MAX_VALUE + 2uL == 1uL)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>wrongWrap<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>ULong.MAX_VALUE + 1uL == 1uL<!>)
}

fun <!VIPER_TEXT!>exactConversions<!>(b: UByte, x: Int): Long {
    preconditions { x >= 0 }
    postconditions<Long> { r -> 0L <= r && r <= 255L }
    verify(x.toUInt().toInt() == x, x.toULong().toLong() == x.toLong(), b.toUInt().toUByte() == b)
    return b.toLong()
}

fun <!VIPER_TEXT!>negativeToUnsigned<!>(x: Int): UInt = <!INTEGER_OVERFLOW!>x.toUInt()<!>

fun <!VIPER_TEXT!>largeToSigned<!>(x: UInt): Int = <!INTEGER_OVERFLOW!>x.toInt()<!>

fun <!VIPER_TEXT!>narrowUnsigned<!>(x: UInt): UByte = <!INTEGER_OVERFLOW!>x.toUByte()<!>

@WrappingNarrowing
fun <!VIPER_TEXT!>reinterpretations<!>() {
    verify((-1).toUInt() == UInt.MAX_VALUE, (-1L).toULong() == ULong.MAX_VALUE, (-1).toByte().toUByte() == UByte.MAX_VALUE)
    verify(UInt.MAX_VALUE.toInt() == -1, 2147483648u.toInt() == Int.MIN_VALUE, ULong.MAX_VALUE.toLong() == -1L)
    verify(300.toUByte() == 44u.toUByte(), 0x1234_5678u.toUShort() == 0x5678u.toUShort(), (-2).toULong() == ULong.MAX_VALUE - 1uL)
}

@WrappingNarrowing
fun <!VIPER_TEXT!>wrongReinterpretation<!>(x: Int) {
    preconditions { x == -1 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x.toUInt() == 1u<!>)
}

fun <!VIPER_TEXT!>unmodelledBitwise<!>(a: UInt, b: UInt): UInt = <!OPAQUE_CALL!>a and b<!>

fun <!VIPER_TEXT!>midpoint<!>(lo: UInt, hi: UInt): UInt {
    preconditions { lo <= hi }
    postconditions<UInt> { r -> lo <= r && r <= hi }
    return lo + (hi - lo) / 2u
}
