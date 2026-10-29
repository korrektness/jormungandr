// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Fields(var count: <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique Int<!>, val name: <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique String?<!>)

fun `unique boolean parameter`(b: <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique Boolean<!>) {}

fun `unique borrowed char parameter`(c: <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique @Borrowed Char<!>) {}

fun <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique Int<!>.`unique int receiver`() {}

fun `unique unit result`(): <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique Unit<!> {}

fun `unique int local`() {
    val i: <!INVALID_VALUE_TYPE_UNIQUENESS!>@Unique Int<!> = <!UNIQUENESS_MISMATCH!>10<!>
}

class SharedFields(var count: Int, val name: String?)

fun `shared int local`() {
    val i: Int = 10 + 10
}

fun `borrowed int parameter`(i: @Borrowed Int) {}

fun `unique reference with value fields`(f: @Unique SharedFields) {
    f.count = f.count + 1
}
