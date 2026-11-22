// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

data <!VIPER_TEXT!>class Point<!>(val x: Int, val y: Int)

fun <!VIPER_TEXT!>componentsInOrder<!>(p: Point): Int {
    postconditions<Int> { it == p.y }
    val (first, _) = p
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return first<!>
}

fun <!VIPER_TEXT!>lambdaComponent<!>(p: Point): Int {
    postconditions<Int> { it == p.x }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return p.let { (_, y) -> y }<!>
}

fun <!VIPER_TEXT!>pairComponent<!>(p: Pair<Int, Int>): Boolean {
    val (<!OPAQUE_CALL!>a<!>, <!OPAQUE_CALL!>_<!>) = p
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == <!OPAQUE_CALL!>p.second<!><!>)
    return true
}
