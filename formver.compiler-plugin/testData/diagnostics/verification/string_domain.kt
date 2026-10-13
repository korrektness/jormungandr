// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

@Pure fun <!VIPER_TEXT!>concatPure<!>(a: String, b: String): String = a + b

fun <!VIPER_TEXT!>pureConcatUnrestricted<!>(a: String, b: String) {
    verify(concatPure(a, b).length >= 0)
}
fun <!VIPER_TEXT!>pureConcatExact<!>(a: String, b: String) {
    preconditions { a.length <= 100; b.length <= 100 }
    verify(concatPure(a, b).length == a.length + b.length)
}
fun <!VIPER_TEXT!>overlongPureResult<!>(a: String, b: String) {
    preconditions { a.length == 2147483647; b.length == 1 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>concatPure(a, b).length == 0<!>)
}
fun <!VIPER_TEXT!>overlongExecutableExit<!>(a: String, b: String) {
    preconditions { a.length == 2147483647; b.length == 1 }
    val joined = a + b
    verify(false)
}
fun <!VIPER_TEXT!>stringValueBounds<!>(s: String, i: Int) {
    preconditions { i >= 0; i < s.length }
    verify(s.length <= 2147483647, s[i].code >= 0, s[i].code <= 65535)
}
fun <!VIPER_TEXT!>concatPreservesCharacterBound<!>(a: String, b: String, lower: Char) {
    preconditions {
        a.length <= 100
        b.length <= 100
        forAll<Int> { (0 <= it && it < a.length) implies (lower <= a[it]) }
        forAll<Int> { (0 <= it && it < b.length) implies (lower <= b[it]) }
    }
    val joined = a + b
    verify(joined.length == a.length + b.length)
    verify(forAll<Int> { (0 <= it && it < joined.length) implies (lower <= joined[it]) })
}
fun <!VIPER_TEXT!>quantifiedStrings<!>() {
    verify(forAll<String> { it.length <= 2147483647 })
}
fun <!VIPER_TEXT!>builderStringBounds<!>(b: @Unique StringBuilder) {
    verify(b.length <= 2147483647, b.toString().length == b.length)
}
fun <!VIPER_TEXT!>overlongBuilderAppend<!>(b: @Unique StringBuilder) {
    preconditions { b.length == 2147483647 }
    b.append('a')
    verify(false)
}
