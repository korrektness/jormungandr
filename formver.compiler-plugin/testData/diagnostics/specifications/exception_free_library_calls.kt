// FULL_JDK
// EXCEPTION_FREE_BY_DEFAULT

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>half<!>(x: Int): Int = x / 2

fun <!VIPER_TEXT!>letLiteral<!>(x: Int): Int = x.let { half(it) }

fun <!VIPER_TEXT!>letThrows<!>(x: Int): Int = x.let { if (it < 0) <!EXCEPTION_FROM_THROW!>throw <!EXCEPTION_FROM_THROW!>IllegalArgumentException()<!><!>; it }

fun <!VIPER_TEXT!>letFunctionObject<!>(x: Int, f: (Int) -> Int): Int = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>x.let(f)<!>

fun <!VIPER_TEXT!>takeIfLiteral<!>(x: Int): Int? = x.takeIf { it > 0 }

fun <!VIPER_TEXT!>takeUnlessLiteral<!>(x: Int): Int? = x.takeUnless { it > 0 }

fun <!VIPER_TEXT!>takeIfThrows<!>(x: Int): Int? = x.takeIf { if (it < 0) <!EXCEPTION_FROM_THROW!>error("negative")<!>; true }

fun <!VIPER_TEXT!>forEachOwned<!>(list: @Unique @Borrowed List<Int>) { list.forEach { half(it) } }

fun <!VIPER_TEXT!>forEachThrows<!>(list: @Unique @Borrowed List<Int>) { list.forEach { <!EXCEPTION_FROM_THROW!>require(it >= 0)<!> } }

fun <!VIPER_TEXT!>forEachShared<!>(list: List<Int>) { <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>list.forEach { half(it) }<!> }

fun <!VIPER_TEXT!>pairs<!>(x: Int): Pair<Int, Int> = <!OPAQUE_CALL!>x to x<!>

fun <!VIPER_TEXT!>constructsPair<!>(x: Int): Pair<Int, Int> = Pair(x, x)

fun <!VIPER_TEXT!>constructsTriple<!>(x: Int): Triple<Int, Int, Int> = Triple(x, x, x)

fun <!VIPER_TEXT!>showsInt<!>(x: Int): String = <!OPAQUE_CALL!>x.toString()<!>

fun <!VIPER_TEXT!>showsNullableInt<!>(x: Int?): String = <!OPAQUE_CALL!>x.toString()<!>

fun <!VIPER_TEXT!>showsChar<!>(c: Char): String = <!OPAQUE_CALL!>c.toString()<!>

fun <!VIPER_TEXT!>showsUInt<!>(x: UInt): String = <!OPAQUE_CALL!>x.toString()<!>

fun <!VIPER_TEXT!>hashesString<!>(s: String): Int = <!OPAQUE_CALL!>s.hashCode()<!>

fun <!VIPER_TEXT!>comparesStrings<!>(s: String, t: String): Boolean = <!OPAQUE_CALL!>s.equals(t)<!>

fun <!VIPER_TEXT!>hashesAny<!>(a: Any): Int = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>a.hashCode()<!>

fun <!VIPER_TEXT!>showsAny<!>(a: Any?): String = <!EXCEPTION_FROM_UNSPECIFIED_CALL, OPAQUE_CALL!>a.toString()<!>

fun <!VIPER_TEXT!>lookupPresent<!>(m: @Unique @Borrowed Map<String, Int>, k: String): Int {
    if (!m.containsKey(k)) return 0
    return m.getValue(k)
}

fun <!VIPER_TEXT!>lookupMissing<!>(m: @Unique @Borrowed Map<String, Int>, k: String): Int = <!EXCEPTION_FROM_THROWING_CALL!>m.getValue(k)<!>

fun <!VIPER_TEXT!>lookupShared<!>(m: Map<String, Int>, k: String): Int = <!EXCEPTION_FROM_UNSPECIFIED_CALL, UNCHECKED_LIBRARY_CALL!>m.getValue(k)<!>
