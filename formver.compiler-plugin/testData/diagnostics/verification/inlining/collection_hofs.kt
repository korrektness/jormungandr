// FULL_JDK
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>positiveList<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
    val allPositive = values.all { it > 0 }
    val anyNonpositive = values.any { it <= 0 }
    verify(allPositive, !anyNonpositive)
}

fun <!VIPER_TEXT!>setSearch<!>(values: @Unique @Borrowed Set<Int>) {
    preconditions { values.size > 0 }
    val nonempty = values.any { true }
    verify(nonempty)
    values.forEach { verify(values.contains(it)) }
}

fun <!VIPER_TEXT!>constantSearch<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size > 0 }
    val anyTrue = values.any { true }
    val anyFalse = values.any { false }
    val allTrue = values.all { true }
    val allFalse = values.all { false }
    verify(anyTrue, !anyFalse, allTrue, !allFalse)
    refute(false)
}

fun <!VIPER_TEXT!>emptySearch<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 0 }
    val anyTrue = values.any { true }
    val anyFalse = values.any { false }
    val allTrue = values.all { true }
    val allFalse = values.all { false }
    verify(!anyTrue, !anyFalse, allTrue, allFalse)
    refute(false)
}

fun <!VIPER_TEXT!>constantFirstIndex<!>(text: String) {
    val firstTrue = text.indexOfFirst { true }
    val firstFalse = text.indexOfFirst { false }
    verify(firstTrue == if (text.length > 0) 0 else -1, firstFalse == -1)
    refute(false)
}

fun <!VIPER_TEXT!>listSearchResults<!>(values: @Unique @Borrowed List<Int>) {
    preconditions {
        values.size == 4
        values[0] == 0; values[1] == 1; values[2] == 2; values[3] == 1
    }
    val first = values.firstOrNull { it > 0 }
    val miss = values.firstOrNull { it < 0 }
    val allPositive = values.all { it > 0 }
    val nonePositive = values.none { it > 0 }
    val anyPositive = values.any { it > 0 }
    val missingIndex = values.indexOfFirst { it < 0 }
    verify(first == 1, miss == null)
    verify(!allPositive, !nonePositive, anyPositive, missingIndex == -1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first == 2<!>)
}

fun <!VIPER_TEXT!>emptyListSearchResults<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 0 }
    val first = values.firstOrNull { it > 0 }
    val allPositive = values.all { it > 0 }
    val nonePositive = values.none { it > 0 }
    verify(first == null, allPositive, nonePositive)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first != null<!>)
}

fun <!VIPER_TEXT!>firstMissNegative<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 1; values[0] == 0 }
    val result = values.firstOrNull { it > 0 }
    verify(result == null)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result != null<!>)
}

fun <!VIPER_TEXT!>allNegative<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 1; values[0] == 0 }
    val result = values.all { it > 0 }
    verify(!result)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result<!>)
}

fun <!VIPER_TEXT!>noneNegative<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 1; values[0] == 1 }
    val result = values.none { it > 0 }
    verify(!result)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result<!>)
}

fun <!VIPER_TEXT!>anyNegative<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 1; values[0] == 1 }
    val result = values.any { it > 0 }
    verify(result)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>!result<!>)
}

fun <!VIPER_TEXT!>indexMissNegative<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 1; values[0] == 0 }
    val result = values.indexOfFirst { it > 0 }
    verify(result == -1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result == 0<!>)
}

fun <!VIPER_TEXT!>entryLoop<!>(values: @Unique @Borrowed Map<Int, Int>) {
    values.forEach { (key, value) ->
        verify(values.containsKey(key))
        verify(values[key] == value)
    }
}

typealias Counts = Map<Int, Int>

fun <!VIPER_TEXT!>aliasedMapEntryLoop<!>(values: @Unique @Borrowed Counts) {
    values.forEach { (key, value) -> verify(values[key] == value) }
}

fun <!VERIFICATION_SKIPPED!>mapAnyRejected<!>(values: Map<Int, Int>) {
    <!UNSUPPORTED_FEATURE!>values.any { it.value > 0 }<!>
}

fun <!VERIFICATION_SKIPPED!>mapCountRejected<!>(values: Map<Int, Int>) {
    <!UNSUPPORTED_FEATURE!>values.count { it.value > 0 }<!>
}

fun <!VERIFICATION_SKIPPED!>groupingFoldRejected<!>(values: Grouping<Int, Int>) {
    <!UNSUPPORTED_FEATURE!>values.fold(0) { total, value -> total + value }<!>
}

fun <!VIPER_TEXT!>foldArgumentOrder<!>(values: @Unique @Borrowed List<Int>): Int {
    preconditions { values.size == 2; values[0] == 3; values[1] == 5 }
    val result = values.fold(0) { total, value ->
        loopInvariants<Int> { prefix ->
            total == if (prefix.size == 0) 0 else if (prefix.size == 1) 3 else 11
        }
        total * 2 + value
    }
    verify(result == 11)
    refute(false)
    return result
}

fun <!VIPER_TEXT!>foldNonnegative<!>(values: @Unique @Borrowed List<Int>): Int {
    postconditions<Int> { result -> result >= 0 }
    return values.fold(0) { total, value ->
        loopInvariants { total >= 0 }
        if (value > 0 && value < 100 && total < 100000) total + value else total
    }
}

fun <!VIPER_TEXT!>visitPrefix<!>(values: @Unique @Borrowed List<Int>): Int {
    var visited = 0
    values.forEach {
        loopInvariants<Int> { prefix -> visited == prefix.size }
        visited++
    }
    verify(visited == values.size)
    return visited
}

fun <!VIPER_TEXT!>foldPrefix<!>(values: @Unique @Borrowed List<Int>): Int {
    val result = values.fold(0) { total, _ ->
        loopInvariants<Int> { prefix -> total == prefix.size }
        total + 1
    }
    verify(result == values.size)
    return result
}

fun <!VERIFICATION_SKIPPED!>javaEntryCallback<!>(values: @Unique @Borrowed Map<Int, Int>) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    <!UNSUPPORTED_FEATURE!>values.forEach { key, value -> verify(key == value) }<!>
}<!>

fun <!VERIFICATION_SKIPPED!>mapFilterRejected<!>(values: Map<Int, Int>) {
    <!UNSUPPORTED_FEATURE!>values.filter { true }<!>
}

fun <!VIPER_TEXT!>mapKeys<!>(values: @Unique @Borrowed Map<Int, Int>, k: Int) {
    val keys: @Unique List<Int> = values.map { it.key }
    verify(keys.size == values.size)
    if (0 <= k && k < keys.size) verify(values.containsKey(keys[k]))
}

fun <!VIPER_TEXT!>mappedList<!>(values: @Unique @Borrowed List<Int>, k: Int) {
    preconditions {
        0 <= k && k < values.size
        forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] < 2147483647) }
    }
    val result: @Unique List<Int> = values.map { it + 1 }
    verify(result.size == values.size)
    verify(result[k] == values[k] + 1)
    verify(result !== values)
}

fun <!VIPER_TEXT!>filteredList<!>(values: @Unique @Borrowed List<Int>, k: Int) {
    val result: @Unique List<Int> = values.filter { it > 0 }
    verify(result.size <= values.size)
    if (0 <= k && k < result.size) verify(result[k] > 0)
    verify(result !== values)
}

fun <!VIPER_TEXT!>filterObserverLimits<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size == 2; values[0] == 1; values[1] == 2 }
    val result: @Unique List<Int> = values.filter { it > 0 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result.size == values.size<!>)
    if (result.size > 0) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result[0] == values[0]<!>)
    refute(false)
}

fun <!VIPER_TEXT!>filterAll<!>(values: @Unique @Borrowed Set<Int>) {
    val result: @Unique List<Int> = values.filter { true }
    verify(result.size == values.size)
}

fun <!VIPER_TEXT!>filterNone<!>(values: @Unique @Borrowed List<Int>) {
    val result: @Unique List<Int> = values.filter { false }
    verify(result.size == 0)
}

fun <!VIPER_TEXT!>collectionAllocatorAbuse<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { values.size > 0 }
    val result: @Unique List<Int> = values.map { it }
    refute(false)
    verify(result[0] == values[0])
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result[0] == 0<!>)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result === values<!>)
}

fun <!VIPER_TEXT!>cleanMeasurements<!>(values: @Unique @Borrowed List<Int>, k: Int): Int {
    val positive: @Unique List<Int> = values.filter { it > 0 && it < 2147483647 }
    val shifted: @Unique List<Int> = positive.map { it + 1 }
    if (0 <= k && k < shifted.size) verify(shifted[k] > 1)
    return shifted.fold(0) { total, _ ->
        loopInvariants<Int> { prefix -> total == prefix.size }
        total + 1
    }
}

fun <!VIPER_TEXT!>temporaryCollection<!>() {
    val result: @Unique List<Int> = listOf(5).map { it + 2 }
    verify(result.size == 1, result[0] == 7)
}

fun <!VIPER_TEXT!>countBounds<!>(values: @Unique @Borrowed List<Int>) {
    val positives = values.count { it > 0 }
    verify(0 <= positives, positives <= values.size)
}
