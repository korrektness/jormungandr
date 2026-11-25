// UNIQUE_CHECK_ONLY

package org.jetbrains.kotlin.formver.plugin

import kotlin.reflect.KClass

fun <S : Any> exposes(spec: KClass<S>, body: <!INVALID_UNIQUENESS_TYPE_TARGET!>(@Unique S)<!>.() -> Unit) {
    check(spec != Any::class)
}
