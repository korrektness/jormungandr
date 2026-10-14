package org.jetbrains.kotlin.formver.external.text

import org.jetbrains.kotlin.formver.plugin.*

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/substring.html: characters from startIndex (inclusive) to endIndex (exclusive).
@ExternalFunction("kotlin.text.substring")
@Pure internal fun String.substringModel(startIndex: Int, endIndex: Int): String = ""
@SpecOf("substringModel") internal fun String.substringSpec(startIndex: Int, endIndex: Int) {
    preconditions { startIndex >= 0 && startIndex <= endIndex && endIndex <= length }
    postconditions<String> { result ->
        result.length == endIndex - startIndex
        forAll<Int> { i -> (i >= 0 && i < result.length) implies (result[i] == this@substringSpec[i + startIndex]) }
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/substring.html: characters from startIndex through the end.
@ExternalFunction("kotlin.text.substring")
@Pure internal fun String.suffixModel(startIndex: Int): String = ""
@SpecOf("suffixModel") internal fun String.suffixSpec(startIndex: Int) {
    preconditions { startIndex >= 0 && startIndex <= length }
    postconditions<String> { result ->
        result.length == length - startIndex
        forAll<Int> { i -> (i >= 0 && i < result.length) implies (result[i] == this@suffixSpec[i + startIndex]) }
    }
}
