// FULL_JDK
// WITH_STDLIB
// LANGUAGE: +MultiPlatformProjects

// MODULE: common
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// FILE: equality.kt

import org.jetbrains.kotlin.formver.plugin.*

// Kotlin `==` calls `equals` (SPECIFICATIONS.md, equality section).

<!VIPER_TEXT!>class AlwaysEq<!>(val v: Int) {
    override fun <!VIPER_TEXT!>equals<!>(other: Any?) = true
    override fun <!VIPER_TEXT!>hashCode<!>() = 0
}

fun <!VIPER_TEXT!>alwaysEq<!>(a: AlwaysEq, b: AlwaysEq) {
    val r = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a === b)<!>)
}

fun <!VIPER_TEXT!>alwaysNotEq<!>(a: AlwaysEq, b: AlwaysEq) {
    val r = a != b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a !== b)<!>)
}

fun <!VIPER_TEXT!>alwaysEqWhen<!>(a: AlwaysEq, b: AlwaysEq) {
    val r = when (a) {
        b -> 1
        else -> 0
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == if (a === b) 1 else 0<!>)
}

data <!VIPER_TEXT!>class D<!>(val v: Int)

fun <!VIPER_TEXT!>dataEq<!>(a: D, b: D) {
    val r = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a === b)<!>)
}

fun <!VIPER_TEXT!>dataEqStructural<!>(a: D, b: D) {
    val r = a == b
    verify(r == (a.v == b.v))
}

<!VIPER_TEXT!>class Plain<!>(val v: Int)

fun <!VIPER_TEXT!>plainEqIsIdentity<!>(a: Plain, b: Plain) {
    val r = a == b
    verify(r == (a === b))
}

fun <!VERIFICATION_SKIPPED!>listEq<!>() {
    val a = listOf(1)
    val b = listOf(1)
    val r = <!UNSUPPORTED_FEATURE!>a == b<!>
    verify(!r)
}

fun <!VIPER_TEXT!>listEqShared<!>(a: List<Int>, b: List<Int>) {
    val r = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a === b)<!>)
}

<!VIPER_TEXT!>class Entity<!>(val id: Int, val name: Int) {
    override fun <!VIPER_TEXT!>equals<!>(other: Any?) = other is Entity && other.id == id
    override fun <!VIPER_TEXT!>hashCode<!>() = id
}

fun <!VIPER_TEXT!>setAddById<!>() {
    val s = mutableSetOf<Entity>()
    s.add(Entity(1, 0))
    val added = s.add(Entity(1, 1))
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>added && s.size == 2<!>)
}

fun <!VIPER_TEXT!>listContainsById<!>() {
    val l = mutableListOf<Entity>()
    l.add(Entity(1, 0))
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>!l.contains(Entity(1, 1))<!>)
}

<!VIPER_TEXT!>class Eq<!>(val v: Int) {
    override fun <!VIPER_TEXT!>equals<!>(other: Any?): Boolean { error("eq") }
    override fun <!VIPER_TEXT!>hashCode<!>() = 0
}

fun <!VIPER_TEXT!>equalsCatch<!>(a: Eq, b: Eq): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val r = a == b; x = 2 } catch (e: Exception) { }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return x<!>
}

// The JVM shares boxes of small values and makes fresh boxes of large ones, so `===` on boxes has no sound model.
fun <!VERIFICATION_SKIPPED!>boxedInts<!>(a: Int) {
    if (a == 1000) { val x: Any = a; val y: Any = a; verify(<!UNSUPPORTED_FEATURE!>x === y<!>) }
}

fun <!VERIFICATION_SKIPPED!>boxedGeneric<!>(a: Any, b: Int) = <!IMPLICIT_BOXING_IN_IDENTITY_EQUALS, UNSUPPORTED_FEATURE!>a === b<!>

// The `actual` class may override `equals`, so `==` on an `expect` class is not identity.
expect <!VIPER_TEXT!>class Key<!>(v: Int)

fun <!VIPER_TEXT!>expectEq<!>(a: Key, b: Key) {
    val r = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a === b)<!>)
}

expect object Single

fun <!VIPER_TEXT!>expectObjectEq<!>(a: Single, b: Any) {
    val r = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == (a === b)<!>)
}

<!VIPER_TEXT!>class Alternating<!> {
    var next = false
    override fun <!VIPER_TEXT!>equals<!>(other: Any?): Boolean {
        next = !next
        return next
    }
    override fun <!VIPER_TEXT!>hashCode<!>() = 0
}

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>elementSearch<!>(arr: @Unique @Borrowed Array<E>, element: E): Int {
    postconditions<Int> { result ->
        result >= -1 && result < arr.size
        (result == -1) == !exists<Int> { i -> (0 <= i && i < arr.size) && element == arr[i] }
        (result >= 0) implies (element == arr[result])
        forAll<Int> { i -> (0 <= i && i < result) implies (element != arr[i]) }
        forAll<Int> { i -> (0 <= i && i < arr.size) implies ((element == arr[i]) == old(element == arr[i])) }
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { k -> (0 <= k && k < i) implies (element != arr[k]) }
            forAll<Int> { k -> (0 <= k && k < arr.size) implies ((element == arr[k]) == old(element == arr[k])) }
        }
        if (element == arr[i]) return i
        i++
    }
    return -1
}

fun <!VIPER_TEXT!>alternatingSearchResults<!>() {
    val arr: @Unique Array<Alternating?> = arrayOfNulls<Alternating>(1)
    val element = Alternating()
    val first = elementSearch(arr, element)
    val second = elementSearch(arr, element)
    // UNSOUND: equals returns true then false, so the searches return 0 then -1 at runtime.
    verify(first == second)
}

// MODULE: jvm()()(common)
// FILE: equality_actual.kt

actual <!VIPER_TEXT!>class Key<!> actual constructor(val v: Int) {
    override fun <!VIPER_TEXT!>equals<!>(other: Any?): Boolean = other is Key && other.v == v
    override fun <!VIPER_TEXT!>hashCode<!>(): Int = v
}

actual <!VIPER_TEXT!>object Single<!> {
    override fun <!VIPER_TEXT!>equals<!>(other: Any?): Boolean = true
    override fun <!VIPER_TEXT!>hashCode<!>(): Int = 0
}
