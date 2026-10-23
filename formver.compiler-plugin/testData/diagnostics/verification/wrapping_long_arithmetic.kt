// FULL_JDK
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

@WrappingLongArithmetic
fun <!VIPER_TEXT!>longBoundaries<!>(max: Long, min: Long) {
    preconditions { max == Long.MAX_VALUE; min == Long.MIN_VALUE }
    verify(max + 1 == min, min - 1 == max, max + max == -2L, min + min == 0L, max - min == -1L)
    verify(Long.MAX_VALUE + 1L == Long.MIN_VALUE)
    verify(min * -1L == min, max * 2 == -2L, min * 2 == 0L)
    verify(max * max == 1L, min * min == 0L)
    refute(false)
    var n = max
    n += 1L
    verify(n == min)
    n -= 1L
    verify(n == max)
    n *= 2L
    verify(n == -2L)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>wrongSignedResults<!>(max: Long) {
    preconditions { max == Long.MAX_VALUE }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>max + 1 > max<!>)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>wrongSignedProduct<!>(min: Long) {
    preconditions { min == Long.MIN_VALUE }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>min * -1L == Long.MAX_VALUE<!>)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>longExactAndNegative<!>(x: Long, y: Long) {
    preconditions { x == -7L; y == 3L }
    verify(x + y == -4L, x - y == -10L, x * y == -21L, x * -y == 21L)
    verify(x * 0L == 0L, x * 1L == x, x + 0 == x, x - 0 == x)
    refute(false)
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>longMixedOperands<!>(max: Long, one: Byte, two: Short, three: Int) {
    preconditions { max == Long.MAX_VALUE; one == 1.toByte(); two == 2.toShort(); three == 3 }
    verify(max + one == Long.MIN_VALUE, one + max == Long.MIN_VALUE)
    verify(max - two == Long.MAX_VALUE - 2, two - max == Long.MIN_VALUE + 3)
    verify(max * three == Long.MAX_VALUE - 2, three * max == Long.MAX_VALUE - 2)
    verify(max * two == -2L, two * max == -2L)
    verify(max - one == Long.MAX_VALUE - 1, one - max == Long.MIN_VALUE + 2)
    verify(max + three == Long.MIN_VALUE + 2, three + max == Long.MIN_VALUE + 2)
}

@Pure @WrappingLongArithmetic
fun <!VIPER_TEXT!>longSuccessor<!>(x: Long): Long {
    postconditions<Long> { it == x + 1 }
    return x + 1
}

@Pure @WrappingLongArithmetic
fun <!VIPER_TEXT!>longProduct<!>(x: Long, y: Long): Long {
    postconditions<Long> { it == x * y }
    return x * y
}

@Pure @WrappingLongArithmetic
fun <!VIPER_TEXT!>specificationOnly<!>(x: Long): Long {
    preconditions { x == Long.MAX_VALUE }
    postconditions<Long> { it == x + 1 }
    return Long.MIN_VALUE
}

fun <!VIPER_TEXT!>checkedLongCaller<!>(x: Long) {
    preconditions { x == Long.MAX_VALUE }
    verify(longSuccessor(x) == Long.MIN_VALUE, specificationOnly(x) == Long.MIN_VALUE)
    verify(longWrappingInline(x) == Long.MIN_VALUE, longProduct(x, x) == 1L)
    <!INTEGER_OVERFLOW!>x + 1<!>
}

@WrappingLongArithmetic
<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>longWrappingInline<!>(x: Long): Long = x + 1

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>longCheckedInline<!>(x: Long): Long = <!INTEGER_OVERFLOW, INTEGER_OVERFLOW!>x + 1<!>

@WrappingLongArithmetic
fun <!VIPER_TEXT!>wrappingLongCaller<!>(x: Long): Long = longCheckedInline(x)

fun <!VIPER_TEXT!>checkedLongOperations<!>(x: Long, choice: Int) {
    preconditions { x == Long.MAX_VALUE }
    when (choice) {
        0 -> <!INTEGER_OVERFLOW!>x + 1<!>
        1 -> <!INTEGER_OVERFLOW!>(-x - 1) - 1<!>
        else -> <!INTEGER_OVERFLOW!>x * 2<!>
    }
}

@WrappingIntArithmetic
fun <!VIPER_TEXT!>intModeLeavesLongChecked<!>(x: Long, choice: Int) {
    preconditions { x == Long.MAX_VALUE }
    when (choice) {
        0 -> <!INTEGER_OVERFLOW!>x + 1<!>
        1 -> <!INTEGER_OVERFLOW!>(-x - 1) - 1<!>
        else -> <!INTEGER_OVERFLOW!>x * 2<!>
    }
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>longModeLeavesIntChecked<!>(x: Int, choice: Int) {
    preconditions { x == Int.MAX_VALUE }
    when (choice) {
        0 -> <!INTEGER_OVERFLOW!>x + 1<!>
        1 -> <!INTEGER_OVERFLOW!>(-x - 1) - 1<!>
        else -> <!INTEGER_OVERFLOW!>x * 2<!>
    }
}

@WrappingLongArithmetic @WrappingIntArithmetic
fun <!VIPER_TEXT!>bothWidths<!>(i: Int, l: Long) {
    preconditions { i == Int.MAX_VALUE; l == Long.MAX_VALUE }
    verify(i + 1 == Int.MIN_VALUE, l + 1 == Long.MIN_VALUE)
    verify((i + 1) - 1 == i, (l + 1) - 1 == l, l * 2 == -2L)
}

@WrappingLongArithmetic @WrappingIntArithmetic
fun <!VIPER_TEXT!>otherLongOperationsStayChecked<!>(x: Long, choice: Int) {
    preconditions { x == Long.MIN_VALUE }
    when (choice) {
        0 -> <!INTEGER_OVERFLOW!>-x<!>
        1 -> <!INTEGER_OVERFLOW!>x / -1<!>
        2 -> <!DIVISION_BY_ZERO, DIVISION_BY_ZERO!>x / 0<!>
        3 -> <!DIVISION_BY_ZERO!>x % 0<!>
        4 -> <!INTEGER_OVERFLOW!>x.toInt()<!>
        5 -> { var n = x; n<!INTEGER_OVERFLOW!>--<!> }
        6 -> { var n = Long.MAX_VALUE; n<!INTEGER_OVERFLOW!>++<!> }
        else -> verify(x % -1 == 0L)
    }
}

fun interface LongLambda {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(y: Long): Long
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>lambdaMode<!>(): @Unique LongLambda = LongLambda { y ->
    exposes(LongLambda::class) { state() == 0 }
    <!INTEGER_OVERFLOW!>y * 2<!>
}

<!VIPER_TEXT!>class LongOperator<!> {
    @Pure
    operator fun <!VIPER_TEXT!>times<!>(other: Long): Long {
        postconditions<Long> { it == 17L }
        return 17L
    }
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>userOperator<!>(x: LongOperator) {
    verify(x * Long.MAX_VALUE == 17L)
}

// The division check is executable source-style detection, with no nonoverflow assumption.
@WrappingLongArithmetic
fun <!VIPER_TEXT!>detectProductOverflow<!>(a: Long, b: Long, choice: Int) {
    preconditions {
        b == 2L
        if (choice == 0) a == Long.MAX_VALUE
        else if (choice == 1) a == Long.MIN_VALUE
        else if (choice == 2) a == Long.MIN_VALUE / 2L
        else if (choice == 3) a == 0L
        else if (choice == 4) a == Long.MAX_VALUE / 2L
        else if (choice == 5) a == Long.MAX_VALUE / 2L + 1L
        else if (choice == 6) a == Long.MIN_VALUE / 2L - 1L
        else a == -7L
    }
    val total = a * b
    when (choice) {
        0 -> {
            verify(total == -2L, total / b != a)
            refute(false)
        }
        1 -> {
            verify(total == 0L, total / b != a)
            refute(false)
        }
        2 -> {
            verify(total == Long.MIN_VALUE, total / b == a)
            refute(false)
        }
        3 -> {
            verify(total == 0L, total / b == a)
            refute(false)
        }
        4 -> {
            verify(total == Long.MAX_VALUE - 1L, total / b == a)
            refute(false)
        }
        5 -> {
            verify(total == Long.MIN_VALUE, total / b != a)
            refute(false)
        }
        6 -> {
            verify(total == Long.MAX_VALUE - 1L, total / b != a)
            refute(false)
        }
        else -> {
            verify(total == -14L, total / b == a)
            refute(false)
        }
    }
}

@WrappingLongArithmetic
fun <!VIPER_TEXT!>negativeMultiplierDetection<!>(a: Long, b: Long) {
    preconditions { a == Long.MIN_VALUE; b == -1L }
    val total = a * b
    verify(total == Long.MIN_VALUE)
    refute(false)
    <!INTEGER_OVERFLOW!>total / b<!>
}
