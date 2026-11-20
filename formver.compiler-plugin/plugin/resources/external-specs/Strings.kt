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

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/starts-with.html: whether the string begins with prefix,
// comparing characters exactly unless ignoreCase.
@ExternalFunction("kotlin.text.startsWith")
@Pure internal fun String.startsWithModel(prefix: String, ignoreCase: Boolean): Boolean = false
@SpecOf("startsWithModel") internal fun String.startsWithSpec(prefix: String, ignoreCase: Boolean) {
    postconditions<Boolean> { result ->
        result implies (prefix.length <= length)
        !ignoreCase implies (result == (prefix.length <= length &&
            forAll<Int> { i -> (i >= 0 && i < prefix.length) implies (this@startsWithSpec[i] == prefix[i]) }))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/equals.html: whether both are null or equal strings,
// comparing characters exactly unless ignoreCase.
@ExternalFunction("kotlin.text.equals")
@Pure internal fun String?.equalsModel(other: String?, ignoreCase: Boolean): Boolean = false
@SpecOf("equalsModel") internal fun String?.equalsSpec(other: String?, ignoreCase: Boolean) {
    postconditions<Boolean> { result ->
        (this@equalsSpec == other) implies result
        result implies ((this@equalsSpec == null) == (other == null))
        !ignoreCase implies (result == (this@equalsSpec == other))
    }
}

// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/to-int.html: the decimal value; throws
// NumberFormatException for an empty string or one that is not a valid Int.
@ExternalFunction("kotlin.text.toInt")
@Pure internal fun String.toIntModel(): Int = 0
@SpecOf("toIntModel") internal fun String.toIntSpec() {
    postconditions<Int> { length > 0 }
}
