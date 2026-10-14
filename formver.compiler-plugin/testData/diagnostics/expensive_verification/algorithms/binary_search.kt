// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*


fun <!VERIFICATION_SKIPPED!>mid_increased_by_one<!>(arr: List<Int>, target: Int): Boolean {
    val size = <!UNCHECKED_LIBRARY_CALL!>arr.size<!>
    val mid = <!UNCHECKED_LIBRARY_CALL!>arr.size<!> / 2 + 1 // if arr.size == 1, mid is out of bounds
    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr.isEmpty()<!> -> false
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> <!UNSUPPORTED_FEATURE!>mid_increased_by_one(arr.subList(mid + 1, size), target)<!>
        else -> <!UNSUPPORTED_FEATURE!>mid_increased_by_one(arr.subList(0, mid), target)<!>
    }
}

fun <!VERIFICATION_SKIPPED!>mid_decreased_by_one<!>(arr: List<Int>, target: Int): Boolean {
    val size = <!UNCHECKED_LIBRARY_CALL!>arr.size<!>
    val mid = <!UNCHECKED_LIBRARY_CALL!>arr.size<!> / 2 - 1 // if arr.size == 1, mid is out of bounds
    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr.isEmpty()<!> -> false
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> <!UNSUPPORTED_FEATURE!>mid_decreased_by_one(arr.subList(mid + 1, size), target)<!>
        else -> <!UNSUPPORTED_FEATURE!>mid_decreased_by_one(arr.subList(0, mid), target)<!>
    }
}

fun <!VERIFICATION_SKIPPED!>mid_decreased_by_one_in_rec_call<!>(arr: List<Int>, target: Int): Boolean {
    val size = <!UNCHECKED_LIBRARY_CALL!>arr.size<!>
    val mid = <!UNCHECKED_LIBRARY_CALL!>arr.size<!> / 2
    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr.isEmpty()<!> -> false
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> <!UNSUPPORTED_FEATURE!>mid_decreased_by_one_in_rec_call(arr.subList(mid + 1, size), target)<!>
        // if arr.size == 1, arr.subList(0, -1) is called
        else -> <!UNSUPPORTED_FEATURE!>mid_decreased_by_one_in_rec_call(arr.subList(0, mid - 1), target)<!>
    }
}

// Bounds keep midpoint arithmetic representable while leaving invalid indices possible.
fun <!VIPER_TEXT!>unsafe_binary_search<!>(arr: List<Int>, target: Int, left: Int, right: Int): Boolean {
    preconditions {
        0 <= left
        right < 2147483647
    }
    if (left > right) {
        return false
    }

    val mid = left + (right - left) / 2

    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> unsafe_binary_search(arr, target, mid + 1, right)
        else -> unsafe_binary_search(arr, target, left, mid - 1)
    }
}
