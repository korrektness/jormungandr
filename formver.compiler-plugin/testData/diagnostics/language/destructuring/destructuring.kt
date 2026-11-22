// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

data <!VIPER_TEXT!>class Point<!>(val x: Int, val y: Int)

data <!VIPER_TEXT!>class Labelled<!>(val label: String, val point: Point)

fun <!VIPER_TEXT!>swapped<!>(p: Point): Point {
    postconditions<Point> { it.x == p.y && it.y == p.x }
    val (x, y) = p
    return Point(y, x)
}

fun <!VIPER_TEXT!>skippedComponent<!>(p: Point): Int {
    postconditions<Int> { it == p.y }
    val (_, y) = p
    return y
}

fun <!VIPER_TEXT!>explicitComponent<!>(p: Point): Boolean {
    postconditions<Boolean> { it }
    return p.component1() == p.x && p.component2() == p.y
}

fun <!VIPER_TEXT!>nested<!>(l: Labelled): Int {
    postconditions<Int> { it == l.point.x }
    val (_, point) = l
    val (x, _) = point
    return x
}

fun <!VIPER_TEXT!>lambdaParameter<!>(p: Point): Int {
    postconditions<Int> { it == p.x }
    return p.let { (x, _) -> x }
}

fun <!VIPER_TEXT!>pairTypes<!>(p: Pair<Int, String>): Int {
    val (<!OPAQUE_CALL!>n<!>, <!OPAQUE_CALL!>s<!>) = p
    return if (s.isEmpty()) 0 else n
}

fun <!VIPER_TEXT!>pairMatchesProperties<!>(p: Pair<Int, Int>): Boolean {
    val (<!OPAQUE_CALL!>a<!>, <!OPAQUE_CALL!>b<!>) = p
    verify(a == <!OPAQUE_CALL!>p.first<!>, b == <!OPAQUE_CALL!>p.second<!>)
    return true
}

fun <!VIPER_TEXT!>tripleTypes<!>(t: Triple<String, Int, Boolean>): Int {
    val (<!OPAQUE_CALL!>s<!>, <!OPAQUE_CALL!>n<!>, <!OPAQUE_CALL!>b<!>) = t
    return if (b && s.isEmpty()) n else 0
}

fun <!VIPER_TEXT!>mapLoop<!>(m: Map<String, Int>): Int {
    for ((k, v) in <!OPAQUE_CALL, UNCHECKED_LIBRARY_CALL, UNCHECKED_LIBRARY_CALL!>m<!>) {
        if (k.isEmpty()) return v
    }
    return 0
}

fun <!VIPER_TEXT!>indexedLoop<!>(xs: List<String>): Int {
    for ((<!OPAQUE_CALL!>i<!>, <!OPAQUE_CALL!>s<!>) in <!OPAQUE_CALL, OPAQUE_CALL, UNCHECKED_LIBRARY_CALL, UNCHECKED_LIBRARY_CALL!>xs.withIndex()<!>) {
        if (s.isEmpty()) return i
    }
    return -1
}

<!VIPER_TEXT!>class Interval<!>(val lo: Int, val hi: Int) {
    operator fun <!VIPER_TEXT!>component1<!>(): Int = lo
    operator fun <!VIPER_TEXT!>component2<!>(): Int = hi
}

operator fun Point.<!VIPER_TEXT!>component3<!>(): Int = x

fun <!VIPER_TEXT!>ordinaryComponents<!>(i: Interval, p: Point): Int {
    val (lo, _) = i
    val (_, _, x) = p
    return if (lo < x) lo else x
}
