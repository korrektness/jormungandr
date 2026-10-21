# ArrayDeque bulk-removal exception safety

Status: prepared, NOT submitted; submission needs operator authorization.

`removeAll` and `retainAll` can leave `ArrayDeque<Int>` containing nulls when the argument's `contains` throws. The attached patch preserves completed removals and keeps the failing element and unprocessed suffix. It changes no verifier assumptions. This is a runtime reproduction and patch, not a SnaKt proof.

## Versions and correspondence

Checked on 2026-10-21:

| Source | Exact revision | Result |
| --- | --- | --- |
| JetBrains/kotlin `v2.3.0` | `f95cb2f7693a15966fd8fd9b1d78d440545e7b66` | Corruption reproduced |
| JetBrains/kotlin fetched `master` | `d834af82174e8fe157db2902d482a44cea316e77` | Corruption reproduced |
| Published `kotlin-stdlib-2.3.0.jar` | SHA-256 `887587c91713250ad52fe14ad9166d042c33835049890e9437f355ffc5a195b1` | Same reproduction output |

Network fetch from `https://github.com/JetBrains/kotlin.git` succeeded. The master commit subject is `KAPT: Do not delete directories KAPT does not own`, dated 2026-10-21. This identifies the fetched tip, not a claim about subsequent upstream changes.

The source checkout is a shared scratch clone at `/tmp/betriz-upstream-scratch/kotlin`, seeded from the read-only `~/dev/kotlin/master`. Its v2.3.0 worktree is `/tmp/betriz-upstream-scratch/pin`. No main checkout is modified.

Toolchain: kotlinc-jvm 2.4.21; Temurin JDK 21.0.10+7-LTS; JUnit 4.13.2. Each source reproduction compiles the **verbatim** upstream `ArrayDeque.kt` with `-Xallow-kotlin-package -Xreturn-value-checker=full -Xfriend-paths=/usr/share/kotlin/lib/kotlin-stdlib.jar`. Its jar precedes the installed stdlib on the runtime classpath. Dependencies such as `AbstractMutableList` come from the installed stdlib. The independent published 2.3.0 jar run confirms the release result without this mixed dependency boundary.

`arraydeque-exception-safety.patch` is a unified diff against fetched master. It includes the source fix and test additions to `libraries/stdlib/test/collections/ArrayDequeTest.kt`; those additions have one home in the patch. `git apply --check` succeeds on v2.3.0, and reverse application checking succeeds on the patched master checkout. Both patched sources pass the tests below.

## Public-API reproduction

`Reproduce.kt` uses `ArrayDeque<Int>`, public construction/end operations, `removeAll`/`retainAll`, iteration, `size`, and summation. Its argument's fourth `contains` call throws a dedicated exception; the argument does not mutate the deque. Capacity is eight, contents are `0..6`. The wrapped case advances the head five positions using public operations before inserting those contents.

The tag source, published 2.3.0 jar and fetched master source all print:

```text
wrapped=false retain=false: [1, 1, 2, 3, 4, 5, 6] size=7
sum=22
wrapped=false retain=true: [0, 2, 2, 3, 4, 5, 6] size=7
sum=22
wrapped=true retain=false: [1, null, null, null, 4, 5, 6] size=7
sum: NullPointerException
wrapped=true retain=true: [0, 2, null, null, 4, 5, 6] size=7
sum: NullPointerException
java.util.ArrayList retain=false: [1, 3, 4, 5, 6] size=5
java.util.ArrayList retain=true: [0, 2, 3, 4, 5, 6] size=6
```

The patched master source prints:

```text
wrapped=false retain=false: [1, 3, 4, 5, 6] size=5
sum=19
wrapped=false retain=true: [0, 2, 3, 4, 5, 6] size=6
sum=20
wrapped=true retain=false: [1, 3, 4, 5, 6] size=5
sum=19
wrapped=true retain=true: [0, 2, 3, 4, 5, 6] size=6
sum=20
java.util.ArrayList retain=false: [1, 3, 4, 5, 6] size=5
java.util.ArrayList retain=true: [0, 2, 3, 4, 5, 6] size=6
```

To reproduce with an existing class-under-test jar:

```sh
kotlinc docs/upstream/arraydeque-removeall/Reproduce.kt -d "$SCRATCH/repro.jar"
java -cp "$CLASS_UNDER_TEST:$SCRATCH/repro.jar:$KOTLIN_HOME/lib/kotlin-stdlib.jar" ReproduceKt
```

For the published release, use the 2.3.0 stdlib jar as the only stdlib, and omit `CLASS_UNDER_TEST`.

## Guarantee analysis

The v2.3.0 KDoc in `libraries/stdlib/src/kotlin/Collections.kt`, `MutableCollection.removeAll` and `retainAll`, describes removal and the Boolean result. Neither documents atomicity or a precise exceptional result. Fetched master has the same relevant wording.

[Java Collection.removeAll](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html#removeAll(java.util.Collection)) and [retainAll](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html#retainAll(java.util.Collection)) likewise specify the successful operation and allowed exceptions, including incompatible elements and null restrictions. They do not promise rollback. A throwing membership test therefore does not by itself establish a defect, and a consistent partial removal is acceptable.

The defect is basic exception safety of the representation: a live `ArrayDeque<Int>` slot contains null despite no null being inserted; reading it as an `Int` then fails during unboxing. `size` still counts the nulled slots as elements. This is a type/content invariant failure, not a numerical out-of-capacity size. The linear case demonstrates a related content failure: compaction duplicates occurrences even though the operation only removes elements. Its size alone still matches its iterable length. A valid partial removal is an order-preserving subsequence of the entry contents; it does not create occurrences or null placeholders.

Neither Kotlin nor Java KDoc explicitly spells out that exceptional subsequence postcondition. The claim is that the implementation damages its own collection invariants, not that either KDoc promises a particular prefix of removals. There is no claim of a strong exception guarantee, callback atomicity, support for callbacks that mutate the receiver, concurrent access, or a specified iterator state after an exception.

The JDK 21 [`ArrayList.batchRemove`](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/ArrayList.java) implementation catches `Throwable`, moves the failing element and unprocessed suffix after the kept prefix, then updates size/modification state and clears vacated slots in `finally`. The reproduction confirms its partial-result behavior on the installed JDK. The Java [`ArrayDeque.bulkRemoveModified`](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/ArrayDeque.java) analogue evaluates predicates into a bitset before physical compaction; inspection shows that a predicate exception precedes its writes. That stronger strategy costs temporary storage and is not required by the proposed Kotlin fix.

## Patch behavior and cost

The three existing scan loops remain: linear, wrapped high segment, wrapped low segment. Predicate evaluation precedes nulling the current slot. Each predicate call catches `Throwable`, restores the suffix without calling user code, and rethrows the same object.

The recovery helper computes the number already removed as the modular distance between the read and write cursors. This distance is less than capacity because the current element remains unprocessed. Zero removals needs no copying. Otherwise a forward circular copy appends the failing element and untouched suffix to the kept prefix, clears the vacated tail, registers a structural modification, and subtracts the removed count from size. The copy stops at the original tail and also covers a full buffer whose head equals tail.

Normal execution remains a single in-place O(n) scan with O(1) extra storage and the same return value, ordering, and membership-call count. Exceptions add an O(n) cleanup scan with O(1) storage. The source adds three exception handlers and one private helper; the normal path's runtime/code-size impact is not benchmarked. This avoids a snapshot allocation or a two-pass membership evaluation. Completed removals remain committed on failure. Restoration assumes the callback does not mutate the deque; asynchronous interruption of restoration is outside the claim.

## Tests and limits

The patch adds `bulkFilterContainsThrows` to the existing `ArrayDequeTest`. It uses its layout enumeration (capacities 2, 8, 15, every enumerated head/tail/size, including full buffers), both public bulk methods, and every possible throwing position. The final position is a non-throwing control. Assertions cover exception identity, exact partial contents, size, indexed reads, and continued end insertion/removal. Existing `removeAll`/`retainAll` tests also cover ordinary behavior.

`run-tests.sh` compiles the complete upstream `ArrayDequeTest` and its actual upstream comparison/platform helpers, then runs JUnit. It also prepends an unpatched source build and requires the same tests to reject it: the negative sibling. No copied algorithm or private-helper-only test substitutes for the public entry points.

Commands run (JUNIT4 and HAMCREST name installed jar paths; output directories are scratch):

```sh
bash docs/upstream/arraydeque-removeall/run-tests.sh \
  /tmp/betriz-upstream-scratch/kotlin /tmp/betriz-upstream-scratch/master-test \
  "$JUNIT4" "$HAMCREST"
bash docs/upstream/arraydeque-removeall/run-tests.sh \
  /tmp/betriz-upstream-scratch/pin /tmp/betriz-upstream-scratch/pin-test \
  "$JUNIT4" "$HAMCREST"
```

| Build | Patched full ArrayDequeTest | Unpatched negative sibling |
| --- | --- | --- |
| Fetched master | 34 tests pass, JUnit time 0.206 s | 34 run, only new regression fails, 0.192 s |
| v2.3.0 | 34 tests pass, JUnit time 0.219 s | 34 run, only new regression fails, 0.194 s |

Times exclude compilation. Compilation emits upstream suppression and test-utility warnings. These are direct JVM source/test executions, not an upstream Gradle stdlib build or cross-platform test run. JS, Wasm and Native execution remains untested. No SnaKt `--verify` run applies to these artifact-only changes; no verification timing or proof is claimed. Shared verifier code is untouched.
