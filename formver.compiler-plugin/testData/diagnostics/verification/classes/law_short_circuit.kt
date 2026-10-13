// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Seq<T> {
    @Pure @get:Unique val size: Int
    @Pure @Unique fun <!VIPER_TEXT!>at<!>(i: Int): T
    @SpecOf("at") @Unique fun atSpec(i: Int) {
        preconditions { 0 <= i && i < size }
    }
    @Law @Pure @Unique fun nonneg(): Boolean = size >= 0
    @Law @Pure @Unique fun headNonNull(): Boolean = size <= 0 || at(0) != null
    @Law @Pure @Unique fun headNonNullIf(): Boolean = if (size > 0) at(0) != null else true
}

<!VIPER_TEXT!>class Single<!><T : Any>(val x: T) : Seq<T> {
    @Pure @get:Unique override val size: Int <!VIPER_TEXT!>get<!>() = 1
    @Pure @Unique override fun <!VIPER_TEXT!>at<!>(i: Int): T = x
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class NullSingle<!><T>(val x: T) : Seq<T> {
    @Pure @get:Unique override val size: Int <!VIPER_TEXT!>get<!>() = 1
    @Pure @Unique override fun <!VIPER_TEXT!>at<!>(i: Int): T = x
}<!>

fun <!VIPER_TEXT!>headOrNull<!>(s: @Unique @Borrowed Seq<String?>?): String? {
    postconditions<String?> { r -> s == null || s.size == 0 || r != null }
    if (s == null) return null
    if (s.size == 0) return null
    return s.at(0)
}

fun <!VIPER_TEXT!>nullableClient<!>(s: @Unique @Borrowed Seq<String?>?) {
    val r = headOrNull(s)
    verify(s == null || s.size == 0 || r != null)
    refute(false)
}

fun <!VIPER_TEXT!>singleClient<!>() {
    val c: @Unique Single<String> = Single("a")
    val s: @Unique Seq<String> = c
    verify(s.size == 1)
    verify(s.at(0) == "a")
    refute(false)
}

fun <!VIPER_TEXT!>nullSingleClient<!>() {
    val c: @Unique NullSingle<String?> = NullSingle(null)
    val s: @Unique Seq<String?> = c
    verify(s.at(0) != null)
    refute(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

fun <!VIPER_TEXT!>outOfDomain<!>(s: @Unique @Borrowed Seq<Int>) {
    val v = <!VIPER_VERIFICATION_ERROR!>s.at(s.size)<!>
}
