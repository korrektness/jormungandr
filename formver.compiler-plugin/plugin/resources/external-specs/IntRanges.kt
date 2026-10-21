package org.jetbrains.kotlin.formver.external.ranges

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("kotlin.ranges.coerceAtLeast")
@Pure internal fun Int.coerceAtLeastModel(minimumValue: Int): Int = 0

@SpecOf("coerceAtLeastModel")
internal fun Int.coerceAtLeastSpecification(minimumValue: Int) {
    postconditions<Int> { result -> result == if (this < minimumValue) minimumValue else this }
}
