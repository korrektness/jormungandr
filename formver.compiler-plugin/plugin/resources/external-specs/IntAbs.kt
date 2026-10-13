package org.jetbrains.kotlin.formver.external.math

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("kotlin.math.abs")
@Pure fun absModel(value: Int): Int = 0

@SpecOf("absModel")
fun absSpecification(value: Int) {
    preconditions { value != -2147483648 }
    postconditions<Int> { result -> result == if (value < 0) -value else value }
}
