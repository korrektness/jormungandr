// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setX<!>(c: @Unique @Borrowed Counter, v: Int) {
    c.x = v
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter) {
    c.x = c.x + 1
}

<!NOTHING_TO_INLINE!>inline<!> fun @Unique @Borrowed Counter.<!VIPER_TEXT!>reset<!>() {
    x = 0
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setBorrowed<!>(c: @Borrowed Counter, v: Int) {
    c.x = v
}

<!NOTHING_TO_INLINE!>inline<!> fun @Borrowed Counter.<!VIPER_TEXT!>resetBorrowed<!>() {
    x = 0
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setFirst<!>(a: @Unique @Borrowed IntArray, v: Int) {
    if (a.size < 1) return
    a[0] = v
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>bumpFirst<!>(a: @Unique @Borrowed IntArray) {
    if (a.size < 1) return
    a[0] += 1
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>readX<!>(c: @Unique @Borrowed Counter): Int = c.x

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterInlineWrite<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    <!UNSUPPORTED_OWNERSHIP!>setX(c, 5)<!>
    verify(c.x == 0)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterInlineBump<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    <!UNSUPPORTED_OWNERSHIP!>bump(c)<!>
    verify(c.x == 0)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterInlineReset<!>(c: @Unique @Borrowed Counter) {
    c.x = 1
    <!UNSUPPORTED_OWNERSHIP!>c.reset()<!>
    verify(c.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineRead<!>(c: @Unique @Borrowed Counter): Int {
    return readX(c)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterInlineElementWrite<!>(a: @Unique @Borrowed IntArray) {
    if (a.size < 1) return
    a[0] = 0
    <!UNSUPPORTED_OWNERSHIP!>setFirst(a, 5)<!>
    verify(a[0] == 0)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterInlineElementBump<!>(a: @Unique @Borrowed IntArray) {
    if (a.size < 1) return
    a[0] = 0
    <!UNSUPPORTED_OWNERSHIP!>bumpFirst(a)<!>
    verify(a[0] == 0)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterBorrowedWrite<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    <!UNSUPPORTED_OWNERSHIP!>setBorrowed(c, 5)<!>
    verify(c.x == 0)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>wrongAfterBorrowedReset<!>(c: @Unique @Borrowed Counter) {
    c.x = 1
    <!UNSUPPORTED_OWNERSHIP!>c.resetBorrowed()<!>
    verify(c.x == 1)
}
