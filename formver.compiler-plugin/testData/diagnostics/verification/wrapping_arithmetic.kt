// FULL_JDK
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

@WrappingIntArithmetic
fun <!VIPER_TEXT!>wrapBoundaries<!>(x: Int, y: Int) {
    preconditions { x == Int.MAX_VALUE; y == Int.MIN_VALUE }
    verify(x + 1 == y, y - 1 == x, x + x == -2, y + y == 0, x - y == -1)
    verify(Int.MAX_VALUE + 1 == Int.MIN_VALUE)
    var n = x
    n += 1
    verify(n == y)
    n -= 1
    verify(n == x)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x + 1 > x<!>)
}

@Pure @WrappingIntArithmetic
fun <!VIPER_TEXT!>wrappingSuccessor<!>(x: Int): Int {
    postconditions<Int> { it == x + 1 }
    return x + 1
}

fun <!VIPER_TEXT!>ordinaryCaller<!>(x: Int) {
    preconditions { x == Int.MAX_VALUE }
    verify(wrappingSuccessor(x) == Int.MIN_VALUE, wrappingInline(x) == Int.MIN_VALUE)
    <!INTEGER_OVERFLOW!>x + 1<!>
}

@WrappingIntArithmetic
<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>wrappingInline<!>(x: Int): Int = x + 1

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>checkedInline<!>(x: Int): Int = <!INTEGER_OVERFLOW, INTEGER_OVERFLOW!>x + 1<!>

@WrappingIntArithmetic
fun <!VIPER_TEXT!>checkedInlineCall<!>(x: Int): Int {
    return checkedInline(x)
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>mixedOperands<!>(x: Int, one: Byte, two: Short) {
    preconditions { x == Int.MAX_VALUE; one == 1.toByte(); two == 2.toShort() }
    verify(x + one == Int.MIN_VALUE, x + two == Int.MIN_VALUE + 1)
    verify(one + x == Int.MIN_VALUE, x - one == Int.MAX_VALUE - 1)
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>otherOperationsStayChecked<!>(x: Int, choice: Int) {
    preconditions { x == Int.MIN_VALUE }
    when (choice) {
        0 -> <!INTEGER_OVERFLOW!>x * 2<!>
        1 -> <!INTEGER_OVERFLOW!>x / -1<!>
        2 -> <!DIVISION_BY_ZERO!>x % 0<!>
        else -> <!INTEGER_OVERFLOW!>-x<!>
    }
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>longStaysChecked<!>(x: Long): Long {
    preconditions { x == Long.MIN_VALUE }
    return <!INTEGER_OVERFLOW!>x - Long.MAX_VALUE<!>
}

private const val maxArraySize = Int.MAX_VALUE - 8

// Body from Kotlin v2.3.0 AbstractList.newCapacity.
@WrappingIntArithmetic
fun <!VIPER_TEXT!>newCapacity<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> { it >= minCapacity }
    // overflow-conscious
    var newCapacity = oldCapacity + (oldCapacity shr 1)
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySize > 0)
        newCapacity = if (minCapacity > maxArraySize) Int.MAX_VALUE else maxArraySize
    return newCapacity
}

fun <!VIPER_TEXT!>checkedCapacity<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> { it >= minCapacity }
    // overflow-conscious
    var newCapacity = <!INTEGER_OVERFLOW!>oldCapacity + (oldCapacity shr 1)<!>
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySize > 0)
        newCapacity = if (minCapacity > maxArraySize) Int.MAX_VALUE else maxArraySize
    return newCapacity
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>capacityAboveArrayLimit<!>(): Int {
    postconditions<Int> { it <= maxArraySize }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return newCapacity(1, Int.MAX_VALUE)<!>
}
