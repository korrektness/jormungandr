// FULL_JDK
// WITH_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>literals<!>() {
    verify(7.floorDiv(2) == 3, (-7).floorDiv(2) == -4, 7.floorDiv(-2) == -4, (-7).floorDiv(-2) == 3)
    verify(7.mod(2) == 1, (-7).mod(2) == 1, 7.mod(-2) == -1, (-7).mod(-2) == -1)
    verify(6.floorDiv(-3) == -2, (-6).mod(3) == 0)
    verify(Byte.MIN_VALUE.floorDiv((-1).toByte()) == 128, Int.MIN_VALUE.floorDiv(-1L) == 2147483648L)
    verify((-7).toShort().floorDiv(2.toByte()) == -4, (-7).toByte().mod(3.toShort()) == 2.toShort())
}

fun <!VIPER_TEXT!>divisorSign<!>(a: Long) {
    val r = a.mod(1000L)
    verify(0L <= r && r < 1000L)
    val s = a.mod(-7L)
    verify(-7L < s && s <= 0L)
}

fun <!VIPER_TEXT!>floorIsNotTruncation<!>(a: Int) {
    preconditions { a in 0..100 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>(-a).floorDiv(2) == -(a / 2)<!>)
}

fun <!VIPER_TEXT!>mixedWidths<!>(millis: Long, b: Byte): Int {
    preconditions { millis >= 0L; b != 0.toByte() }
    val seconds: Long = millis.floorDiv(1000)
    val rest: Int = millis.mod(1000)
    verify(seconds * 1000L + rest == millis)
    val small: Byte = 1000.mod(b)
    return rest + small
}

fun <!VIPER_TEXT!>zeroDivisor<!>(a: Int, b: Int): Int {
    preconditions { a >= 0 }
    return <!DIVISION_BY_ZERO!>a.floorDiv(b)<!>
}

fun <!VIPER_TEXT!>zeroModulus<!>(a: Long, b: Int): Int = <!DIVISION_BY_ZERO!>a.mod(b)<!>

fun <!VIPER_TEXT!>minByMinusOne<!>(a: Int): Int {
    preconditions { a == Int.MIN_VALUE }
    return <!INTEGER_OVERFLOW!>a.floorDiv(-1)<!>
}

fun <!VIPER_TEXT!>inSpecification<!>(a: Int): Int {
    preconditions { a >= 0 }
    postconditions<Int> { result -> result == a.mod(10) }
    return a % 10
}

<!VIPER_TEXT!>class Slot<!>(index: Long) {
    val slot: Int = index.mod(8)
    @Law @Pure fun inRange(): Boolean = slot == slot.mod(8)
}

fun <!VIPER_TEXT!>slotInLaw<!>(index: Long) {
    verify(Slot(index).slot < 8)
}
