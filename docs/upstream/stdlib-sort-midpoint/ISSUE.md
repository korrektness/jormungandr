# Draft YouTrack issue (project KT, subsystem Libraries. Stdlib)

**Title:** Unsigned and Native/Wasm/JS array sorts overflow `Int` when computing the midpoint on large arrays

**Kotlin version:** 2.3.0 and current master (`b38a1bb230a213b92205ec6c937b557e86d21d3b`).

**Problem.** The quicksort and merge sort helpers compute `(left + right) / 2` / `(start + end) / 2` in `Int`. When the sum exceeds `Int.MAX_VALUE` it wraps negative and the sort reads or writes a negative index.

Affected: `libraries/stdlib/src/kotlin/collections/UArraySorting.kt` (all platforms including JVM), `libraries/stdlib/native-wasm/src/kotlin/collections/ArraySorting.kt`, `libraries/stdlib/js/src/kotlin/collections/ArraySorting.kt`.

**Reproducer (JVM, `-Xmx3g`):**

```kotlin
@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    UByteArray(2147483639).sort()
}
```

**Actual:** `java.lang.ArrayIndexOutOfBoundsException: Index -536870919 out of bounds for length 2147483639` at `UArraySorting.kt` (`partition`).

**Kotlin/Native 2.3.0**, `ByteArray(2147483639).sort()`: `Uncaught Kotlin exception: kotlin.ArrayIndexOutOfBoundsException`, exit status 134.

**Expected:** the array is sorted.

**Fix.** Use the idiom already used by the stdlib's binary search, `(low + high).ushr(1)`; indices are non-negative, so the unsigned shift is exact. 15 one-line replacements, no behavior change for smaller ranges.
