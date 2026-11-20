// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.preconditions
import org.jetbrains.kotlin.formver.plugin.verify
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

<!VIPER_TEXT!>class Counter<!>(val start: Int)

<!VIPER_TEXT!>class Limit<!>(val max: Int)

context(counter: Counter)
fun <!VIPER_TEXT!>startOf<!>(): Int {
    postconditions<Int> { it == counter.start }
    return counter.start
}

context(counter: Counter, limit: Limit)
fun <!VIPER_TEXT!>clamped<!>(delta: Int): Int {
    preconditions { delta >= 0; delta <= 100; counter.start <= 100 }
    postconditions<Int> { it <= limit.max }
    val sum = counter.start + delta
    return if (sum > limit.max) limit.max else sum
}

context(counter: Counter)
fun <!VIPER_TEXT!>forwardsContext<!>(): Int {
    postconditions<Int> { it == counter.start }
    return startOf()
}

context(limit: Limit)
fun <!VIPER_TEXT!>forwardsBothContexts<!>(counter: Counter): Int {
    preconditions { limit.max >= 0; counter.start <= 100 }
    postconditions<Int> { it <= limit.max }
    return context(counter) { clamped(0) }
}

fun <!VIPER_TEXT!>withReceiverContext<!>(counter: Counter): Int {
    postconditions<Int> { it == counter.start }
    return with(counter) { startOf() }
}

fun <!VIPER_TEXT!>withExplicitContext<!>() {
    val result = context(Counter(3)) { startOf() }
    verify(result == 3)
}

@OptIn(ExperimentalContracts::class)
context(present: Counter?)
fun <!VIPER_TEXT!>hasCounter<!>(fallback: Counter?): Boolean {
    contract {
        returns(true) implies (present != null)
    }
    return present != null && fallback == null
}

context(present: Counter?)
fun <!VIPER_TEXT!>startOrZero<!>(): Int = if (hasCounter(null)) present.start else 0

context(counter: Counter)
fun Limit.<!VIPER_TEXT!>offset<!>(first: Int, second: Int = 0): Int {
    preconditions { first >= 0; first <= 100; second >= 0; second <= 100; max >= 0; max <= 100; counter.start >= 0; counter.start <= 100 }
    postconditions<Int> { it == counter.start + max - first + second }
    return counter.start + max - first + second
}

fun <!VIPER_TEXT!>namedArgumentsWithContext<!>(limit: Limit): Int {
    preconditions { limit.max >= 0; limit.max <= 100 }
    postconditions<Int> { it == 4 + limit.max }
    return context(Counter(1)) { limit.offset(second = 5, first = 2) }
}

inline fun <R> <!VIPER_TEXT!>runWithCounter<!>(counter: Counter, block: context(Counter) () -> R): R = block(counter)

fun <!VIPER_TEXT!>inlineContextLambda<!>() {
    val result = runWithCounter(Counter(5)) { startOf() }
    verify(result == 5)
}

<!VIPER_TEXT!>class Scaled<!>(val factor: Int) {
    <!UNSUPPORTED_FEATURE!>context(counter: Counter)
    val scaledStart: Int
        <!UNSUPPORTED_FEATURE!><!VERIFICATION_SKIPPED!>get<!>() = counter.start * factor<!><!>
}

fun <!VERIFICATION_SKIPPED!>readsContextProperty<!>(counter: Counter, scaled: Scaled): Int = with(counter) { scaled.scaledStart }
