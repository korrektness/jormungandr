// UNIQUE_CHECK_ONLY

package org.jetbrains.kotlin.formver.plugin

import kotlin.reflect.KClass

fun <S : Any> exposes(spec: KClass<S>, body: (@Unique S).() -> Unit) = Unit

fun <S : Any> exposes(spec: S, body: <!INVALID_UNIQUENESS_TYPE_TARGET!>(@Unique S)<!>.() -> Unit) = Unit
