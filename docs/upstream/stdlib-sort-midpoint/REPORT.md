# Stdlib sort midpoint overflow

Status: prepared, NOT submitted; submission needs operator authorization.

Stdlib sort helpers compute a midpoint as `(left + right) / 2` (`(start + end) / 2` in merge sort). For ranges whose index sum exceeds `Int.MAX_VALUE` the sum wraps negative, and quicksort reads `array[negative]`. Correctness only; the reproducer is an all-zero array, no adversarial input.

## Upstream status (checked 2026-11-20)

| Source | Revision | Sites |
| --- | --- | --- |
| JetBrains/kotlin `v2.3.0` | `f95cb2f7693a15966fd8fd9b1d78d440545e7b66` | 15 |
| JetBrains/kotlin `master` (shallow clone of github.com) | `b38a1bb230a213b92205ec6c937b557e86d21d3b` (2026-11-20) | 15 |

Sites, identical at both revisions: `libraries/stdlib/src/kotlin/collections/UArraySorting.kt` 4 (`UByteArray`, `UShortArray`, `UIntArray`, `ULongArray` partition); `libraries/stdlib/native-wasm/src/kotlin/collections/ArraySorting.kt` 10 (2 merge sort, 8 partition: Byte, Short, Int, Long, Char, Float, Double, Boolean); `libraries/stdlib/js/src/kotlin/collections/ArraySorting.kt` 1 (comparator merge sort). `git log` on these three files shows no change touching the expression. The stdlib's own idiom is `(low + high).ushr(1) // safe from overflows` (`libraries/stdlib/src/kotlin/collections/Collections.kt`, binary search).

Existing reports: none found.
- YouTrack REST search, project KT (`https://youtrack.jetbrains.com/api/issues?query=project: KT <terms>`), terms: `UArraySorting`, `ArraySorting`, `sort overflow`, `quickSort`, `mergeSort`, `midpoint`, `sort UByteArray large`, `sortWith large array`, `UByteArray.sort`, `sort ArrayIndexOutOfBoundsException negative index`, `IntArray.sort out of bounds`. The only sort-bounds hit is [KT-70005](https://youtrack.jetbrains.com/issue/KT-70005) (empty-range `sort(0)` crash on Native/Wasm), unrelated to the midpoint.
- GitHub: `gh search issues` and `gh search prs` on JetBrains/kotlin for `sort overflow`, `quickSort overflow`, `ArraySorting midpoint`, `sort ArrayIndexOutOfBounds large array` returned no results; `gh search code` shows the expression still on master. Kotlin issues live in YouTrack, so a GitHub miss is weak evidence.
- Web search (two queries) found no report.
Search tools match on text, so a report phrased differently could be missed.

## JVM reproduction

`reproduce-jvm.sh` compiles `Reproduce.kt` (`UByteArray(2147483639).sort()`, all zeros) and runs it against the installed `kotlin-stdlib.jar` of kotlinc 2.3.0 (Temurin 21.0.10, `-Xmx3g`, ~35 s). Output (first lines; exit status 1):

```text
info: kotlinc-jvm 2.3.0 (JRE 21.0.10+7-LTS)
Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index -536870919 out of bounds for length 2147483639
	at kotlin.UByteArray.get-w2LRezQ(UByteArray.kt:29)
	at kotlin.collections.UArraySortingKt.partition-4UcCI2c(UArraySorting.kt:14)
	at kotlin.collections.UArraySortingKt.quickSort-4UcCI2c(UArraySorting.kt:34)
```

Why: after the first partition of an all-equal array the right half is `[~2^30, 2^31-10]`; `left + right` exceeds `Int.MAX_VALUE`. The exact index depends on the array contents.

Patched check: `verify-patched-jvm.sh <UArraySorting.kt>` copies the file's source into package `copied` with private functions made public (the stdlib's mangled entry points otherwise cannot be replaced on the classpath) and runs `quickSort` on the same input. Unpatched master file: `Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index -536870919 out of bounds for length 2147483639`, exit status 1. Patched file: `sorted 2147483639 elements`, exit status 0 (~55 s each).

## Kotlin/Native reproduction

`reproduce-native.sh` compiles `ReproduceNative.kt` (`ByteArray(2147483639).sort()`) with the published `kotlin-native-prebuilt-linux-x86_64-2.3.0` (tarball SHA-256 `60d4f92af2cd06dacf3e01e574c433cecca2e4b22fddefadaae40c4f531a567e`) for linux_x64, with a scratch `KONAN_DATA_DIR`. Toolchain installed in under 10 minutes. Output:

```text
Uncaught Kotlin exception: kotlin.ArrayIndexOutOfBoundsException
    at 0   repro.kexe                          0x22ee32           ThrowArrayIndexOutOfBoundsException + 194
    at 1   repro.kexe                          0x231c99           kfun:kotlin.collections.quickSort#internal + 409
```

Exit status 134. Patched Native not run: it needs a stdlib build from source.

## Patch

`sort-midpoint-overflow.patch`: 15 one-line replacements, `(a + b) / 2` to `(a + b).ushr(1)`. Correct because indices are non-negative and below `2^31`, so the sum is below `2^32` and the unsigned shift recovers it. For non-overflowing sums the result is unchanged. `git apply --check` succeeds in scratch worktrees of master `b38a1bb2…` and `v2.3.0`. No test is included: a regression test needs a ~2 GiB array (or ~4 GiB for reference arrays) and does not fit upstream stdlib test runs. Not built with upstream Gradle; JS, Wasm and Native execution of the patched source untested.

## Public entry points

All are reached only when the range is large enough for the sum to exceed `Int.MAX_VALUE`: a range ending past `2^30` for the fromIndex-to-toIndex forms, an array above ~`2^30` elements for merge sort (recursion's right half starts at `~2^30`). Merge-sort overflow was not reproduced (reference arrays of that size). Indirect callers (`sorted()`, `sortedArray()`, `sortedDescending()`, `sortedWith()`, `MutableList.sort()/sortWith()` via `toTypedArray`) reach these through the sorts below.

**`UArraySorting.kt` (common source, every platform incl. JVM)**: `UByteArray`, `UShortArray`, `UIntArray`, `ULongArray`: `sort()` and `sort(fromIndex, toIndex)`.

**`native-wasm/ArraySorting.kt` (Kotlin/Native, Kotlin/Wasm)**:
- quicksort: `ByteArray`, `ShortArray`, `IntArray`, `LongArray`, `CharArray`, `FloatArray`, `DoubleArray`: `sort()` and `sort(fromIndex, toIndex)` (`BooleanArray` has an internal sort only; the quickSort overload has no public caller found).
- merge sort: `Array<T: Comparable<T>>.sort()` and `.sort(fromIndex, toIndex)`; `Array<T>.sortWith(comparator)` and `.sortWith(comparator, fromIndex, toIndex)`; `MutableList<T>.sort()/sortWith()` (copies to an array first).

**`js/ArraySorting.kt` (Kotlin/JS, merge sort only)**: ranged `Array<T: Comparable<T>>.sort(fromIndex, toIndex)`, `LongArray.sort(fromIndex, toIndex)`, `CharArray.sort(fromIndex, toIndex)`, `Array<T>.sortWith(comparator, fromIndex, toIndex)`; and, only when the engine's `Array.sort` is not stable, `Array<T: Comparable<T>>.sort()`, `Array<T>.sortWith(comparator)`, `Array<T>.sort(comparison)`, `MutableList.sortWith()` (via `collectionsSort`). Other JS primitive-array sorts use typed-array or native `sort`.

JVM: primitive/`Array`/`MutableList` sorts use `java.util`; only the unsigned-array sorts above reach these files.

Entry points were derived from `_UArrays.kt`, `_ArraysNative.kt`, `_ArraysWasm.kt`, `_ArraysJs.kt` and `collectionJs.kt`/`MutableCollections.kt` on master; not compiled per platform.
