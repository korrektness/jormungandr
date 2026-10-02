// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Unique

class Box<T>(var item: <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T<!>)

fun <T> `unique parameter`(x: <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T<!>) {}

fun <T> `unique nullable parameter`(x: <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T?<!>) {}

fun <T> <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T<!>.`unique receiver`() {}

interface Maker {
    fun <T> `unique result`(): <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T<!>
}

fun <T> `unique local`() {
    val y: <!INVALID_TYPE_PARAMETER_UNIQUENESS!>@Unique T<!>
}
