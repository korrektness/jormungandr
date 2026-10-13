// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Calls of function objects, which have no contract (docs/soundness.md, H3).

class Box(var n: Int)

fun <!VIPER_TEXT!>callbackMutation<!>(c: @Unique @Borrowed Box, f: (@Borrowed Box) -> Unit) {
    c.n = 1
    f(c)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.n == 1<!>)
}

// A function type cannot take ownership, so an owned argument is always a loan.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>callbackUnique<!>(c: @Unique @Borrowed Box, f: (<!INVALID_UNIQUENESS_TYPE_TARGET!>@Unique @Borrowed Box<!>) -> Unit) {
    f(c)
}

class Holder(var child: @Unique Box)

fun <!VIPER_TEXT!>callbackNested<!>(h: @Unique @Borrowed Holder, f: (@Borrowed Holder) -> Unit) {
    h.child.n = 1
    f(h)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>h.child.n == 1<!>)
}

fun <!VIPER_TEXT!>callbackOtherArgument<!>(c: @Unique @Borrowed Box, d: @Unique @Borrowed Box, f: (@Borrowed Box) -> Unit) {
    c.n = 1
    f(d)
    verify(c.n == 1)
    refute(false)
}

fun <!VIPER_TEXT!>callbackKeepsOwnership<!>(c: @Unique @Borrowed Box, f: (@Borrowed Box) -> Unit) {
    f(c)
    c.n = 2
    verify(c.n == 2)
    refute(false)
}

<!VIPER_TEXT!>class CountedBox<!>(var n: Int) {
    init { require(n >= 0) }
    @Law @Pure @Unique fun nonnegative(): Boolean = n >= 0
}

// `n` is law-bound, so no verified code writes it through a shared path, and the callback leaves the law holding.
fun <!VIPER_TEXT!>lawAfterCallback<!>(c: @Unique @Borrowed CountedBox, f: (@Borrowed CountedBox) -> Unit) {
    c.n = 5
    f(c)
    verify(c.n >= 0)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.n == 5<!>)
}
