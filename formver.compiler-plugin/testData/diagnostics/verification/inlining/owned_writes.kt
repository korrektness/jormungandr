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
fun <!VIPER_TEXT!>wrongAfterInlineWrite<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    setX(c, 5)
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterInlineBump<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    bump(c)
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterInlineReset<!>(c: @Unique @Borrowed Counter) {
    c.x = 1
    c.reset()
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineRead<!>(c: @Unique @Borrowed Counter): Int {
    return readX(c)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterInlineElementWrite<!>(a: @Unique @Borrowed IntArray) {
    if (a.size < 1) return
    a[0] = 0
    setFirst(a, 5)
    verify(<!VIPER_VERIFICATION_ERROR!>a[0] == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterInlineElementBump<!>(a: @Unique @Borrowed IntArray) {
    if (a.size < 1) return
    a[0] = 0
    bumpFirst(a)
    verify(<!VIPER_VERIFICATION_ERROR!>a[0] == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterBorrowedWrite<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    setBorrowed(c, 5)
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongAfterBorrowedReset<!>(c: @Unique @Borrowed Counter) {
    c.x = 1
    c.resetBorrowed()
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 1<!>)
}
