// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Instant.kt, safeAddOrElse, safeMultiplyOrElse, Instant.toEpochMilliseconds
// Adaptation: the inline `action: () -> Nothing` parameter becomes a throw; the Instant receiver becomes
// (epochSeconds, nanosecondsOfSecond) parameters constrained by the class's documented ranges.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val MIN_SECOND = -31557014167219200L
private const val MAX_SECOND = 31556889864403199L
private const val NANOS_PER_MILLI = 1_000_000
private const val MILLIS_PER_SECOND = 1_000

/**
 * Safely adds two long values.
 * @throws ArithmeticException if the result overflows a long
 */
private fun <!VIPER_TEXT!>safeAdd<!>(a: Long, b: Long): Long {
    postconditions<Long> { result ->
        ((b >= 0L) implies (a <= Long.MAX_VALUE - b && result == a + b)) &&
            ((b < 0L) implies (a >= Long.MIN_VALUE - b && result == a + b))
    }
    val sum = <!INTEGER_OVERFLOW!>a + b<!>
    // check for a change of sign in the result when the inputs have the same sign
    if ((a xor sum) < 0 && (a xor b) >= 0) {
        throw ArithmeticException()
    }
    return sum
}

@Pure
private fun <!VIPER_TEXT!>productFitsLong<!>(a: Long, b: Long): Boolean {
    return if (b == 0L) true
    else if (b == -1L) a != Long.MIN_VALUE
    else if (b > 0L) a >= Long.MIN_VALUE / b && a <= Long.MAX_VALUE / b
    else a >= Long.MAX_VALUE / b && a <= Long.MIN_VALUE / b
}

/**
 * Safely multiply a long by a long.
 * @return the new total
 * @throws ArithmeticException if the result overflows a long
 */
private fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>safeMultiply<!>(a: Long, b: Long): Long {
    postconditions<Long> { result ->
        productFitsLong(a, b) && result == a * b
    }
    if (b == 1L) {
        return a
    }
    if (a == 1L) {
        return b
    }
    if (a == 0L || b == 0L) {
        return 0
    }
    val total = a * b
    if (total / b != a || a == Long.MIN_VALUE && b == -1L || b == Long.MIN_VALUE && a == -1L) {
        throw ArithmeticException()
    }
    return total
}

/**
 * Returns the number of milliseconds from the epoch instant `1970-01-01T00:00:00Z`.
 * Any fractional part of a millisecond is rounded toward zero to the whole number of milliseconds.
 * If the result does not fit in [Long], returns [Long.MAX_VALUE] for a positive result or [Long.MIN_VALUE] for a negative result.
 */
fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>toEpochMilliseconds<!>(epochSeconds: Long, nanosecondsOfSecond: Int): Long {
    preconditions {
        MIN_SECOND <= epochSeconds && epochSeconds <= MAX_SECOND
        0 <= nanosecondsOfSecond && nanosecondsOfSecond <= 999_999_999
    }
    if (epochSeconds >= 0) {
        val millis = try { safeMultiply(epochSeconds, MILLIS_PER_SECOND.toLong()) } catch (e: ArithmeticException) {
            return Long.MAX_VALUE
        }
        return try { safeAdd(millis, (nanosecondsOfSecond / NANOS_PER_MILLI).toLong()) } catch (e: ArithmeticException) {
            return Long.MAX_VALUE
        }
    } else {
        val millis = try { safeMultiply(epochSeconds + 1, MILLIS_PER_SECOND.toLong()) } catch (e: ArithmeticException) {
            return Long.MIN_VALUE
        }
        return try { safeAdd(millis, (nanosecondsOfSecond / NANOS_PER_MILLI - MILLIS_PER_SECOND).toLong()) } catch (e: ArithmeticException) {
            return Long.MIN_VALUE
        }
    }
}
