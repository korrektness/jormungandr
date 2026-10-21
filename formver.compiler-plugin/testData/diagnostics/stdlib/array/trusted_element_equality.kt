// FULL_JDK
// TRUST_REPORT

import org.jetbrains.kotlin.formver.plugin.*

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>trustedIndexOf<!>(arr: @Unique @Borrowed Array<E>, element: E): Int {
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

fun <E> <!VIPER_TEXT!>untrustedIndexOf<!>(arr: @Unique @Borrowed Array<E>, element: E): Int {
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
            forAll<Int> { k -> (0 <= k && k < i) implies (element != <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>arr[k]<!>) }
            forAll<Int> { k -> (0 <= k && k < arr.size) implies ((element == arr[k]) == old(element == arr[k])) }
        }
        if (element == arr[i]) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return i<!>
        i++
    }
    return -1
}

fun <E> <!VIPER_TEXT!>caller<!>(arr: @Unique @Borrowed Array<E>, element: E) {
    val first = trustedIndexOf(arr, element)
    val second = trustedIndexOf(arr, element)
    verify(first == second)
    val actual = element == element
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>actual == (element == element)<!>)
}

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>nullableOperators<!>(a: E?, b: E?) {
    val unequal = a != b
    verify(unequal == (a != b))
    val branch = when (a) { b -> true; else -> false }
    verify(branch == (a == b))
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == a<!>)
}

@TrustedElementEquality
fun <E : Any> <!VIPER_TEXT!>nonNullOperators<!>(a: E, b: E) {
    val equal = a == b
    verify(equal == (a == b))
}

data <!VIPER_TEXT!>class Wrapped<!><E>(val element: E)

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>structuralLeft<!>(a: Wrapped<E>, b: Wrapped<E>) {
    val equal = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>equal == (a == b)<!>)
}

interface Element

@TrustedElementEquality
fun <!VIPER_TEXT!>interfaceLeft<!>(a: Element, b: Element) {
    val first = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first == (a == b)<!>)
}

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>caughtEquality<!>(a: E, b: E): Int {
    postconditions<Int> { it == 1 || it == 2; it == 2 }
    try { val r = a == b } catch (e: Exception) { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return 1<!> }
    return 2
}

<!NOTHING_TO_INLINE!>inline<!> fun <E> <!VIPER_TEXT!>untrustedInline<!>(a: E, b: E): Boolean = a == b

@TrustedElementEquality
<!NOTHING_TO_INLINE!>inline<!> fun <E> <!VIPER_TEXT!>trustedInline<!>(a: E, b: E): Boolean = a == b

@TrustedElementEquality
fun <E> <!VIPER_TEXT!>annotatedCaller<!>(a: E, b: E) {
    val result = untrustedInline(a, b)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result == (a == b)<!>)
}

fun <E> <!VIPER_TEXT!>inlineCaller<!>(a: E, b: E) {
    val result = trustedInline(a, b)
    verify(result == (a == b))
    val own = a == b
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>own == (a == b)<!>)
}
