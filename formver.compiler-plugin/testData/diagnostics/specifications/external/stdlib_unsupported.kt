// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>listVarargs<!>() = <!UNSUPPORTED_FEATURE!>listOf(1, 2)<!>
fun <!VERIFICATION_SKIPPED!>mutableListVarargs<!>() = <!UNSUPPORTED_FEATURE!>mutableListOf(1, 2)<!>
fun <!VERIFICATION_SKIPPED!>listIterator<!>(list: List<Int>) { <!UNSUPPORTED_FEATURE!>list.iterator()<!> }
fun <!VERIFICATION_SKIPPED!>listSubList<!>(list: List<Int>) { <!UNSUPPORTED_FEATURE!>list.subList(0, 0)<!> }
fun <!VERIFICATION_SKIPPED!>mapEntries<!>(map: Map<Int, Int>) { <!UNSUPPORTED_FEATURE!>map.entries<!> }
fun <!VERIFICATION_SKIPPED!>stringUppercase<!>() = <!UNSUPPORTED_FEATURE!>"text".uppercase()<!>
