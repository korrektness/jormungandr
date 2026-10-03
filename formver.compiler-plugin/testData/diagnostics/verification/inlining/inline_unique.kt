// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>probe<!>(c: @Unique @Borrowed Counter): Int = c.x

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setX<!>(c: @Unique @Borrowed Counter, v: Int) {
    c.x = v
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter) {
    c.x = c.x + 1
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>bumpTwice<!>(c: @Unique @Borrowed Counter) {
    bump(c)
    bump(c)
}

<!NOTHING_TO_INLINE!>inline<!> fun @Unique @Borrowed Counter.<!VIPER_TEXT!>reset<!>() {
    x = 0
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>consume<!>(c: @Unique Counter): Int = c.x

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>pass<!>(c: @Unique Counter): @Unique Counter = c

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>countTo<!>(c: @Unique @Borrowed Counter, n: Int): Int {
    c.x = 0
    var i = 0
    while (i < n) {
        loopInvariants { i >= 0 && c.x == i }
        c.x = c.x + 1
        i = i + 1
    }
    return i
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setShared<!>(c: @Borrowed Counter, v: Int) {
    c.x = v
}

inline fun <!VIPER_TEXT!>setSharedThen<!>(c: @Borrowed Counter, f: () -> Unit) {
    c.x = 5
    f()
}

inline fun <!VIPER_TEXT!>readShared<!>(c: Counter, f: () -> Int): Int = f()

fun <!VIPER_TEXT!>sink<!>(c: @Unique Counter) {}

inline fun <!VIPER_TEXT!>consumeThen<!>(c: @Unique Counter, f: () -> Int): Int {
    sink(c)
    return f()
}

@AlwaysVerify
fun <!VIPER_TEXT!>readThroughInline<!>(c: @Unique @Borrowed Counter) {
    c.x = 3
    verify(probe(c) == 3)
}

@AlwaysVerify
fun <!VIPER_TEXT!>writeThroughInline<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    setX(c, 5)
    verify(c.x == 5)
}

@AlwaysVerify
fun <!VIPER_TEXT!>bumpThroughInline<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    bump(c)
    verify(c.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>nestedInlining<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    bumpTwice(c)
    verify(c.x == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongNestedInlining<!>(c: @Unique @Borrowed Counter) {
    c.x = 0
    bumpTwice(c)
    verify(<!VIPER_VERIFICATION_ERROR!>c.x == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>extensionReceiver<!>(c: @Unique @Borrowed Counter) {
    c.x = 1
    c.reset()
    verify(c.x == 0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>consumedArgument<!>() {
    val v: @Unique Counter = Counter(3)
    verify(consume(v) == 3)
}

@AlwaysVerify
fun <!VIPER_TEXT!>uniqueResultIntoLocal<!>(c: @Unique Counter) {
    val d: @Unique Counter = pass(c)
    d.x = 4
    verify(d.x == 4)
}

@AlwaysVerify
fun <!VIPER_TEXT!>loopInInlinedBody<!>(c: @Unique @Borrowed Counter, n: Int) {
    val k = countTo(c, n)
    verify(c.x == k)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineCallInPostcondition<!>(c: @Unique @Borrowed Counter): Int {
    postconditions<Int> { r -> r == probe(c) }
    return c.x
}

@AlwaysVerify
fun <!VIPER_TEXT!>borrowedWriteToOwnedLocal<!>() {
    val v: @Unique Counter = Counter(0)
    setShared(v, 5)
    verify(v.x == 5)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongBorrowedWriteToOwnedLocal<!>() {
    val v: @Unique Counter = Counter(0)
    setShared(v, 5)
    verify(<!VIPER_VERIFICATION_ERROR!>v.x == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongBorrowedWriteSeenByLambda<!>() {
    val v: @Unique Counter = Counter(0)
    v.x = 0
    setSharedThen(v) { verify(<!VIPER_VERIFICATION_ERROR!>v.x == 0<!>) }
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>lambdaReadsArgumentOfPlainParameter<!>() {
    val v: @Unique Counter = Counter(0)
    readShared(v) { <!UNSUPPORTED_OWNERSHIP!>v.x<!> }
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>lambdaReadsConsumedArgument<!>() {
    val v: @Unique Counter = Counter(0)
    consumeThen(v) { <!UNSUPPORTED_OWNERSHIP!>v.x<!> }
}
