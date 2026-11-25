// UNIQUE_CHECK_ONLY

package org.jetbrains.kotlin.formver.plugin

abstract class Predicate(val exp: Any)

data class UniquePred(val data: Any) : Predicate(data)
