// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>addOverflow<!>(x: Int): Int {
    preconditions { x == 2147483647 }
    return <!INTEGER_OVERFLOW!>x + 1<!>
}
fun <!VIPER_TEXT!>subOverflow<!>(x: Int): Int {
    preconditions { x == -2147483648 }
    return <!INTEGER_OVERFLOW!>x - 1<!>
}
fun <!VIPER_TEXT!>mulOverflow<!>(x: Int): Int {
    preconditions { x == 1073741824 }
    return <!INTEGER_OVERFLOW!>x * 2<!>
}
fun <!VIPER_TEXT!>negOverflow<!>(x: Int): Int {
    preconditions { x == -2147483648 }
    return <!INTEGER_OVERFLOW!>-x<!>
}
fun <!VIPER_TEXT!>divOverflow<!>(x: Int): Int {
    preconditions { x == -2147483648 }
    return <!INTEGER_OVERFLOW!>x / -1<!>
}
fun <!VIPER_TEXT!>incOverflow<!>(x: Int): Int {
    preconditions { x == 2147483647 }
    var y = x
    y<!INTEGER_OVERFLOW!>++<!>
    return y
}
fun <!VIPER_TEXT!>decOverflow<!>(x: Int): Int {
    preconditions { x == -2147483648 }
    var y = x
    y<!INTEGER_OVERFLOW!>--<!>
    return y
}
fun <!VIPER_TEXT!>charAddOverflow<!>(c: Char): Char {
    preconditions { c == '\uffff' }
    return <!INTEGER_OVERFLOW!>c + 1<!>
}
fun <!VIPER_TEXT!>charSubOverflow<!>(c: Char): Char {
    preconditions { c == '\u0000' }
    return <!INTEGER_OVERFLOW!>c - 1<!>
}
fun <!VIPER_TEXT!>zeroDiv<!>(x: Int): Int = <!DIVISION_BY_ZERO, DIVISION_BY_ZERO!>x / 0<!>
fun <!VIPER_TEXT!>zeroRem<!>(x: Int): Int = <!DIVISION_BY_ZERO!>x % 0<!>

fun <!VIPER_TEXT!>signedDivision<!>(x: Int, y: Int) {
    preconditions { x == -3; y == 2 }
    verify(x / y == -1, x % y == -1, x / -y == 1, x % -y == -1)
}
fun <!VIPER_TEXT!>minRemainder<!>(x: Int) {
    preconditions { x == -2147483648 }
    verify(x % -1 == 0)
}
fun <!VIPER_TEXT!>boundedOperations<!>(x: Int, y: Int) {
    preconditions { x >= -100; x <= 100; y >= 1; y <= 100 }
    verify(x + y >= -99, x - y <= 99, x * y <= 10000, -x >= -100, x / y >= -100)
}
@Pure fun <!VIPER_TEXT!>pureOverflow<!>(x: Int): Int = <!INTEGER_OVERFLOW!>x + 1<!>
fun <!VIPER_TEXT!>postOverflow<!>(x: Int): Int {
    postconditions<Int> { <!INTEGER_OVERFLOW!>it + 1<!> > it }
    return x
}
fun <!VIPER_TEXT!>guardedSpec<!>(x: Int): Int {
    postconditions<Int> { (it < 2147483647) implies (it + 1 > it) }
    return x
}
fun <!VIPER_TEXT!>inputBounds<!>(x: Int, c: Char) {
    verify(x >= -2147483648, x <= 2147483647, c >= '\u0000', c <= '\uffff')
}
fun <!VIPER_TEXT!>discardOverflow<!>(x: Int) {
    <!INTEGER_OVERFLOW!>x + 1<!>
}
fun <!VIPER_TEXT!>preconditionOverflow<!>(x: Int): Int {
    preconditions { <!INTEGER_OVERFLOW!>x + 1<!> > x }
    return x
}
fun <!VIPER_TEXT!>loopOverflow<!>(x: Int) {
    var i = 0
    while (i < 1) {
        loopInvariants { <!INTEGER_OVERFLOW!>x + 1<!> > x; i >= 0; i <= 1 }
        i++
    }
}
<!VIPER_TEXT!>class CheckedArithmeticLaw<!>(val x: Int) {
    @Law @Pure fun grows(): Boolean = <!INTEGER_OVERFLOW!>x + 1<!> > x
}
fun <!VIPER_TEXT!>shortCircuit<!>(x: Int) {
    verify(x == 2147483647 || x + 1 > x)
    if (x == 2147483647) return
    verify(x + 1 > x)
}
fun <!VIPER_TEXT!>compoundOverflow<!>(x: Int): Int {
    preconditions { x == 2147483647 }
    var y = x
    <!INTEGER_OVERFLOW!>y += 1<!>
    return y
}
fun <!VIPER_TEXT!>quantifierBounds<!>() {
    verify(forAll<Int> { it >= -2147483648; it <= 2147483647 })
}
fun <!VIPER_TEXT!>quantifierOverflow<!>() {
    verify(forAll<Int> { <!INTEGER_OVERFLOW!>it + 1<!> > it })
}
fun <!VIPER_TEXT!>constantOverflow<!>(): Int = <!INTEGER_OVERFLOW!>2147483647 + 1<!>
fun <!VIPER_TEXT!>constantNormalization<!>() {
    verify(-2147483648 < 0, -3 / 2 == -1, -3 % 2 == -1)
}
// Both bounds can fail; the operation is reported once.
fun <!VIPER_TEXT!>eitherBoundOverflow<!>(x: Int, y: Int): Int = <!INTEGER_OVERFLOW!>x - y<!>
