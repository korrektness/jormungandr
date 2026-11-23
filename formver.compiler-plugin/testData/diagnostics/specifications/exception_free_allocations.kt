// FULL_JDK
// EXCEPTION_FREE_BY_DEFAULT

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>concatenates<!>(s: String, t: String): String {
    postconditions<String> { r -> r.length - s.length == t.length }
    return s + t
}

fun <!VIPER_TEXT!>appendsChar<!>(s: String, c: Char): String = s + c

fun <!VIPER_TEXT!>concatenatesInt<!>(s: String, n: Int): String = s + n

fun <!VIPER_TEXT!>templates<!>(n: Int, s: String, c: Char, b: Boolean?): String = "n=$n s=$s c=$c b=$b"

fun <!VIPER_TEXT!>concatenatesAny<!>(s: String, a: Any): String = <!EXCEPTION_FROM_UNSPECIFIED_CALL!>s + a<!>

fun <!VIPER_TEXT!>templatesAny<!>(a: Any): String = <!EXCEPTION_FROM_UNSPECIFIED_CALL!>"a=$a"<!>

fun <!VIPER_TEXT!>buildsInPlace<!>(): String {
    postconditions<String> { r -> r == "ab" }
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append('a')
    sb.append("b")
    return sb.toString()
}

fun <!VIPER_TEXT!>buildsWithCapacity<!>(n: Int): String {
    val sb: @Unique StringBuilder = <!EXCEPTION_FROM_UNSPECIFIED_CALL!>StringBuilder(n)<!>
    sb.append('a')
    return sb.toString()
}

fun <!VIPER_TEXT!>buildsString<!>(s: String): String = buildString { append(s) }

fun <!VIPER_TEXT!>buildsStringThrowing<!>(n: Int): String = buildString { if (n < 0) <!EXCEPTION_FROM_THROW!>throw <!EXCEPTION_FROM_THROW!>IllegalArgumentException()<!><!>; append('x') }

fun <!VIPER_TEXT!>buildsList<!>(): List<Int> = buildList { }

<!VIPER_TEXT!>class Box<!> {
    var v: Int = 0
}

fun <!VIPER_TEXT!>appliesLiteral<!>(): Box = Box().apply { v = 1 }

fun <!VIPER_TEXT!>appliesFunctionObject<!>(configure: Box.() -> Unit): Box = <!EXCEPTION_FROM_FUNCTION_OBJECT!>Box().apply(configure)<!>

fun <!VIPER_TEXT!>alsoFunctionObject<!>(x: Int, f: (Int) -> Unit): Int = <!EXCEPTION_FROM_FUNCTION_OBJECT!>x.also(f)<!>

fun <!VIPER_TEXT!>lists<!>(x: Int): List<Int> = <!OPAQUE_CALL!>listOf(x, x)<!>

fun <!VIPER_TEXT!>empty<!>(): List<String> = emptyList()

fun <!VIPER_TEXT!>mutableList<!>(x: Int): MutableList<Int> = <!OPAQUE_CALL!>mutableListOf(x)<!>

fun <!VIPER_TEXT!>singletonSet<!>(a: Any): Set<Any> = setOf(a)

fun <!VIPER_TEXT!>intSet<!>(x: Int, y: Int): Set<Int> = <!OPAQUE_CALL!>setOf(x, y)<!>

fun <!VIPER_TEXT!>anySet<!>(a: Any, b: Any): Set<Any> = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>setOf(a, b)<!>

fun <!VIPER_TEXT!>anyMap<!>(a: Any): Map<Any, Int> = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>mapOf(<!OPAQUE_CALL!>a to 1<!>, <!OPAQUE_CALL!>a to 2<!>)<!>

fun <!VIPER_TEXT!>addsToLocal<!>(x: Int) {
    val list = mutableListOf<Int>()
    list.add(x)
}

fun <!VIPER_TEXT!>addsToArrayList<!>(x: Int) {
    val list = ArrayList<Int>()
    <!OPAQUE_CALL!>list.add(x)<!>
}

<!VIPER_TEXT!>class Tags<!> {
    private val tags = mutableListOf<String>()

    fun <!VIPER_TEXT!>tag<!>(t: String) {
        <!UNCHECKED_LIBRARY_CALL!>tags.add(t)<!>
    }
}

fun <!VIPER_TEXT!>addsToParameter<!>(list: MutableList<Int>, x: Int) {
    <!EXCEPTION_FROM_UNSPECIFIED_CALL, UNCHECKED_LIBRARY_CALL!>list.add(x)<!>
}

fun <!VIPER_TEXT!>addsToOwnedParameter<!>(list: @Unique @Borrowed MutableList<Int>, x: Int) {
    preconditions { list.size < 2147483647 }
    <!EXCEPTION_FROM_UNSPECIFIED_CALL!>list.add(x)<!>
}

fun <!VIPER_TEXT!>addsToVar<!>(x: Int) {
    var list: MutableList<Int> = mutableListOf()
    <!EXCEPTION_FROM_UNSPECIFIED_CALL, UNCHECKED_LIBRARY_CALL!>list.add(x)<!>
}

fun <!VIPER_TEXT!>insertsIntoLocal<!>(x: Int) {
    val list = mutableListOf<Int>()
    <!EXCEPTION_FROM_UNSPECIFIED_CALL!>list.add(0, x)<!>
}
