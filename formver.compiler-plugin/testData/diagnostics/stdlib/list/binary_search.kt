// FULL_JDK
// USE_STDLIB


fun <!VERIFICATION_SKIPPED!>safe_binary_search<!>(arr: List<Int>, target: Int): Boolean {
    val size = <!UNCHECKED_LIBRARY_CALL!>arr.size<!>
    val mid = <!UNCHECKED_LIBRARY_CALL!>arr.size<!> / 2 // +1 or -1 throws IndexOutOfBound
    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr.isEmpty()<!> -> false
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> <!UNSUPPORTED_FEATURE!>safe_binary_search(arr.subList(mid + 1, size), target)<!>
        else -> <!UNSUPPORTED_FEATURE!>safe_binary_search(arr.subList(0, mid), target)<!> // mid - 1 throws IllegalArgumentException
    }
}

fun <!VIPER_TEXT!>unsafe_binary_search_fixed<!>(arr: List<Int>, target: Int, left: Int, right: Int): Boolean {
    if (left > right || left < 0 || right >= <!UNCHECKED_LIBRARY_CALL!>arr.size<!>) {
        return false
    }

    val mid = left + (right - left) / 2

    return when {
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> == target -> true
        <!UNCHECKED_LIBRARY_CALL!>arr[mid]<!> < target -> unsafe_binary_search_fixed(arr, target, mid + 1, right)
        else -> unsafe_binary_search_fixed(arr, target, left, mid - 1)
    }
}
