# Target: Kotlin stdlib `ArrayDeque`

Use this note when deciding whether to point SnaKt at `kotlin.collections.ArrayDeque`, and when planning that work.
It is a research and design note: nothing here is implemented. Statements are tagged **[E]** for evidence checked
while writing this note (a command, a source location or a run), and **[H]** for hypotheses that a stage below has to
confirm.

## Recommendation

Select ArrayDeque at the minimal deliverable (M, below). Do not commit to the full refinement (F) until M has
measured verification times.

- The target has upstream defects. The pin carries fixes for two wrap-around bugs (KT-80390, KT-80661). The
  2.3.0 source has one open defect and one unspecified aliasing behaviour. Both reproduce, and neither has a
  matching report (see Evidence). Both are known without a proof. A proof's further upstream value is assurance
  for the remaining shift-heavy methods, and the layout tests upstream already cover those well **[H]**.
- The verifier changes it needs are general: element models for generic arrays, `copyInto` with overlapping
  ranges, `shr`, executed `==` on type parameters, and law checks on exceptional exits. Every array-backed
  container needs them.
- It delivers something to SnaKt users: a bundled ArrayDeque specification whose contracts a verified port
  proves. Today a client of `ArrayDeque` gets only the opaque library call.
- SnaKt as it stands cannot detect either new finding. The defect breaks content preservation on an exceptional exit, and
  exceptional exits carry no obligations. The aliasing case is ruled out by the ownership discipline as a
  precondition rather than detected. To find bugs of the first kind, M is not enough: it needs stage F3.
- Compared with persistent collections and Ktor ranges: `docs/targets/` holds no note on either, so the
  comparison under "Selection" rests on the targets' shape.

## Pin

- Upstream: JetBrains/kotlin tag `v2.3.0`, the Kotlin version SnaKt builds against
  (`gradle/libs.versions.toml`, `kotlin = "2.3.0"`). **[E]**
- Files: `libraries/stdlib/src/kotlin/collections/ArrayDeque.kt` (672 lines),
  `libraries/stdlib/test/collections/ArrayDequeTest.kt` (754 lines), helpers in
  `libraries/stdlib/src/kotlin/collections/AbstractList.kt` (`checkElementIndex`, `checkPositionIndex`,
  `checkRangeIndexes`, `newCapacity`). **[E]**
- Inspect with `git -C ~/dev/kotlin/master show v2.3.0:libraries/stdlib/src/kotlin/collections/ArrayDeque.kt`.

## Deployment relevance

- ArrayDeque is the only deque in common Kotlin, so it is the deque on JS, Wasm and Native, and the default
  worklist and queue type in multiplatform code. **[E]** (it lives in the common `src` set).
- The Kotlin compiler uses it in production code: 59 non-test files at `v2.3.0` mention `ArrayDeque` without
  importing `java.util.ArrayDeque`, among them FIR resolve, the checkers and the CFG copier. **[E]** (a
  `git grep` over `compiler analysis core plugins native js wasm libraries`).
- Usage outside JetBrains is not measured. **[gap]**

## The upstream class

Representation: `head: Int`, `elementData: Array<Any?>`, `size: Int` (an `override var` with a private setter).
Live elements sit at `elementData[(head + i) mod capacity]` for `i in 0 until size`. Wrap-around is computed with
compare-and-subtract (`positiveMod`, `negativeMod`, `incremented`, `decremented`), not with `%` or bitmasks.
`companion.emptyElementData` is a shared empty sentinel. `ArrayDeque()`, `ArrayDeque(0)` and `ArrayDeque(c)` with
an empty `c` start on it; `ArrayDeque(n)` with `n > 0` allocates `arrayOfNulls(n)`, and `ArrayDeque(c)` with a
non-empty `c` adopts `c.toTypedArray()`.

| Concern | Where | Notes |
|---|---|---|
| Indexing | `internalIndex`, `get`, `set`, `first`/`last` | Bounds come from `AbstractList.checkElementIndex`. |
| Growth | `ensureCapacity`, `copyElements`, `AbstractList.newCapacity` | `newCapacity` relies on two's-complement wrap ("overflow-conscious"), and `ensureCapacity(size + 1)` detects overflow through a negative `minCapacity`. |
| End insertion and removal | `addFirst`/`addLast`, `removeFirst`/`removeLast`, `*OrNull` | `NoSuchElementException` on empty. Removed slots are nulled. |
| Middle insertion and removal | `add(index, e)`, `removeAt`, `addAll(index, c)`, `removeRange` + `removeRangeShift*` | Picks the shorter side to shift. Up to three `copyInto` calls on the same array with overlapping ranges. |
| Search | `indexOf`, `lastIndexOf`, `contains`, `remove(e)` | Two-segment scans when the window wraps. The site of KT-80390 and KT-80661. |
| Bulk filter | `removeAll`, `retainAll` via inline `filterInPlace` | Calls a foreign `Collection.contains` in the middle of the compaction. |
| Ownership | `elementData` is private and never returned; `toArray` copies | `ArrayDeque(Collection)` adopts the array from `toTypedArray()` without copying. |
| Generic storage | `elementData[i] as E` (unchecked) | Erased, so the cast is a no-op at run time. |
| Concurrent modification | `registerModification` increments the inherited `modCount` | Iterators come from `AbstractMutableList`. |
| Platform | `toArray` overrides (JVM only), `copyInto` actuals | JVM: `System.arraycopy`. JS: `arrayCopy`, a loop that copies backwards when ranges overlap. Wasm: `copyWasmArray`. Native: `copyImpl` intrinsic. |

The upstream tests (`ArrayDequeTest.testArrayDeque`) enumerate every head/tail layout for buffers of size 0, 2, 8
and 15, `indexOf` and `lastIndexOf` included. **[E]** No test
covers a throwing `contains` argument or a deque passed to its own `addAll`. **[E]**

## Evidence

### Regression cases KT-80390 and KT-80661 [E]

At the pin, the wrapped branch of both `indexOf` and `lastIndexOf` is guarded by `isNotEmpty() && head >= tail`.
The regression cases are two mutants of that condition. The KT-80390 mutant of `indexOf` drops `isNotEmpty() &&`,
leaving `head >= tail`: `indexOf(null)` on a cleared deque then scans the nulled buffer and returns an index. The
KT-80661 mutant of `lastIndexOf` replaces the condition with `head > tail`: `lastIndexOf` then returns -1 on a
full buffer. A postcondition of the `ListModel.indexOf` form in
`formver.compiler-plugin/plugin/resources/external-specs/Collections.kt` rejects both mutants. The `null`
case is in reach already, because the executed `==` against `null` is exact. The present-element case needs
verifier change V5.

### New defect 1: `removeAll`/`retainAll` corrupt the deque when `contains` throws [E]

If `elements.contains` throws, the deque keeps its `size` while its live window holds nulls or duplicates. The
reproductions, observed results, versions and mechanism are in issue draft A.

Classification: a violated guarantee. Neither the `MutableCollection.removeAll` KDoc nor the `java.util` contract
promises atomicity when an exception is thrown. The guarantee broken here is a different one: the deque's
contents must be a sequence of values of type `E`, each of which was added. After the call, an
`ArrayDeque<Int>` returns `null` from `get`, and `size` counts a phantom element.

### Finding 2: `addAll(index, this)` inserts the wrong elements [E]

When `elements` is the deque itself, the inserted elements are read from the already shifted buffer. The
reproduction, observed results, versions and mechanism are in issue draft B.

Classification: unspecified aliasing behaviour, not a violated guarantee. **[E]**
- At `v2.3.0`, the `MutableList.addAll(index, elements)` KDoc (`libraries/stdlib/src/kotlin/Collections.kt`) says
  the elements "are inserted in the order they appear in the [elements] collection". It says nothing about
  `elements` being the receiver, and nothing about `elements` changing while the call runs.
- On the JVM, `ArrayDeque` is a `java.util.List`. The `java.util.List.addAll` Javadoc calls the behaviour undefined
  when the argument is modified while the operation is in progress, and notes that this happens when the argument
  is the list itself.
- KT-55285 (`removeAll(subList(...))` on an ArrayDeque) is in state "As Designed". The maintainer's comment says a
  view's behaviour is unspecified once the deque is structurally modified.
- The result is a divergence from `java.util.ArrayList`, which snapshots its argument. It is not a broken
  contract.

A YouTrack search for `project:KT ArrayDeque` (about 45 issues) has no report of either finding. **[E]**

### What SnaKt can and cannot express today [E]

| Need | Status in SnaKt | Source |
|---|---|---|
| `Array<Any?>` elements | Opaque library call, may throw | docs/soundness.md, "Arrays" |
| `copyInto`, `fill`, `arrayOfNulls` | Not modelled for any array type | no occurrence in `core/src` or `testData` |
| `Int.shr`, `Int.and` | Not executable intrinsics (`FullySpecialKotlinFunction.kt` has none) | a law may name them (`ClassLawConstruction.kt::isLawValueOperation`) |
| Wrapping `+` in `newCapacity` | Every `+` must stay in range; overflow is `INTEGER_OVERFLOW` | SPECIFICATIONS.md, "Integer arithmetic" |
| `element == elementData[i]` with `E` unbounded | Executed: an unconstrained Boolean. Specified: `EqualsRelation` | `conversion/KotlinEquality.kt::EqualityLowering` |
| Subclassing `AbstractMutableList` | Rejected: a source subtype of a modelled type is an `INVALID_EXTERNAL_SPEC` | SPECIFICATIONS.md, "External specifications"; `testData/diagnostics/stdlib/list/custom_list.kt` |
| `override var size` | An overriding property is a call and carries no stable facts | SPECIFICATIONS.md, "Rejected constructs" |
| Class laws on exceptional exit | Not checked. Outside `try`, a call has no exceptional edge | docs/soundness.md, "Unreachable points" |
| Iterating a `Collection` argument with `iterator()` | Unsupported; only the inline templates iterate | SPECIFICATIONS.md, "Collection iteration" |
| Pointwise sequence contracts (`size`, `get(i)`, frame quantifiers) | Supported, in the style of `MutableListModel` | `external-specs/Collections.kt` |
| Array-backed list refining an interface | Supported for `IntArray` | `testData/diagnostics/verification/classes/intlist_milestone.kt` |
| Owned `IntArray` element sequence | Supported | SPECIFICATIONS.md, "`IntArray`" |

## Proof properties

Abstract state: `size` and `get(i)` for `0 <= i < size`, as `@Pure` observers. The abstract sequence is
pointwise: `get(i) == elementData[internalIndex(i)]`. This needs no constructor for sequence values, which
`IterationSequence` does not have.

P1. Representation law: `0 <= size <= elementData.size`; `elementData.size == 0 || 0 <= head < elementData.size`;
`elementData.size == 0 implies head == 0`.

P2. Null hygiene: every slot outside the live window is `null`. Upstream relies on it: `copyElements` copies the
whole buffer, and the KT-80390 mutant of `indexOf` is wrong only because of what P2 puts in the empty slots. P2 is a
quantified class law over the buffer.

P3. Per-operation contracts equal to the bundled `MutableListModel` contracts where the operation exists there
(`add`, `add(index)`, `set`, `remove`, `get`, `indexOf`, `contains`). The deque operations get the analogous
pointwise form: `addFirst` gives `get(0) === e` and `get(i + 1) === old(get(i))`.

P4. Bounds behaviour: a contract with `index in 0 until size` as its precondition (the bundled style), and
`first()` with `size > 0`. The throwing paths are left out of the contracts, because SnaKt claims nothing on
abnormal exit.

P5. Overflow: `addFirst`, `addLast` and `add` take `size < Int.MAX_VALUE`, as `MutableListModel.add` does. Under
that precondition the `"Deque is too big."` branch is dead. `newCapacity` is verified under V4.

P6. Exception safety (F only), on every exceptional exit of a member that borrows its receiver:
(a) P1 and P2 hold;
(b) for `removeAll`, `retainAll` and the other removal members, the abstract sequence is a subsequence of its
value at entry, witnessed by a ghost index map (V8);
(c) for the other members, the abstract sequence equals its value at entry or the postcondition's result.
New defect 1 keeps (a): head, size and capacity stay valid, and the nulled or overwritten slots are inside the live
window. It breaks (b): `[1, null, null]` is not a subsequence of `[1, 2, null]`, and `[1, 1, 2, …]` is not a
subsequence of `[0, 1, 2, …]`.

P7. Non-aliasing (F only): `addAll(index, elements)` takes `elements` as `@Unique @Borrowed`, so a client's
`d.addAll(i, d)` is an ownership error. This forbids finding 2. It does not fix it.

Not claimed: termination of foreign `equals`/`contains`, `modCount` and iterator fail-fast behaviour, `toArray`,
`hashCode`/`equals` of the deque, platform `copyInto` implementations.

## Deliverables

### M: verified port of the core operations, plus a bundled specification

- A port of the upstream class in `testData` that changes only what the deviation list allows. Members: the
  constructors except `ArrayDeque(Collection)`, `isEmpty`, `get`, `set`, `first`/`last` (+`OrNull`),
  `addFirst`/`addLast`, `add(e)`, `removeFirst`/`removeLast` (+`OrNull`), `indexOf`, `lastIndexOf`, `contains`,
  `clear`, and the private helpers they call (`ensureCapacity`, `copyElements`, `newCapacity`, the mod helpers,
  `nullifyNonEmpty`).
- A source interface `DequeSpec<E>` holding P3's contracts. The port implements it, so override refinement proves
  the port against it (SPECIFICATIONS.md, "Overrides").
- `external-specs/ArrayDeque.kt` binding `kotlin.collections.ArrayDeque`. Its contracts are the text of
  `DequeSpec`, and a test checks the two are equal.
- Negative siblings: the KT-80390 mutant of `indexOf` (`head >= tail`) and the KT-80661 mutant of `lastIndexOf`
  (`head > tail`) fail.
- This covers the BFS/worklist use of the class and the code of both regression cases. It does not cover
  middle insertion or removal, bulk operations, or exception safety.

### F: full sequential refinement

M plus `add(index, e)`, `removeAt`, `remove(e)`, `addAll` (both), `ArrayDeque(Collection)`, `removeRange` and its
shift loops, `removeAll`/`retainAll` with P6, and P7. F needs V6 (obligations on exceptional exits), V7
(iterating an owned collection argument) and V8 (a ghost index map for subsequences). Each of these is a research-grade change.
`removeRange`'s loops run at most three times over overlapping segments, so they need invariants over segment
arithmetic. A solver-time risk sits there **[H]**.

M against F: M carries most of the SnaKt-side value (bundled spec, general array features, both regression cases).
F adds the only proof route to the class of new defect 1, plus coverage of the shift code. The layout
enumeration upstream already tests that code at sizes up to 15 **[H: low odds of further functional bugs]**.

## Verifier changes

| # | Change | Generality | Size |
|---|---|---|---|
| V1 | `Array<T>` element model: the predicate holds a `Seq[Ref]`; reads and writes through a unique path; `size`; `arrayOfNulls(n)`, `emptyArray()`; elements typed `T` under the existing type-invariant assumption | every generic container | 2–3 sol changes |
| V2 | `copyInto(dst, off, from, to)` for `IntArray` and `Array<T>` with the documented semantics for the same array and overlapping ranges: `dst' = dst[..off] ++ old(src)[from..to] ++ dst[off+len..]`, bounds as obligations; `fill(v, from, to)` | every array algorithm | 1–2 sol changes |
| V3 | `x shr s` for a constant `s` as `floorDiv(x, 2^(s and 31))`, the arithmetic shift with Kotlin's masked distance. Upstream uses only `shr 1`. `ushr` and `and` stay unmodelled; each needs its own unsigned or bitwise encoding | broad | 1 sol change |
| V4 | Wrapping arithmetic where a function says so (D3) | narrow but recurring in JDK-derived code | 1 sol change after D3 |
| V5 | Executed `==` of kind `Dynamic` returns `EqualsRelation(l, r)` and may throw, which extends the "Element equality in collections" assumption to executed code | every generic collection | 1 sol change + register update |
| V6 | Obligations at the exceptional exits of members that borrow a unique receiver: class laws, and a declared exceptional postcondition that may refer to entry values | broad; high churn | opus design, then 2–4 sol changes |
| V7 | `iterator()`/`hasNext()`/`next()` on an owned `Collection` against its iteration sequence | broad | opus design, then 2–3 sol changes |
| V8 | A ghost index map that witnesses one sequence as an order-preserving subsequence of another, usable in normal and exceptional postconditions | narrow | opus design |
| V9 | A source subtype of a modelled library class, verified against the model (conformance), so that the upstream class can be checked without the port | broad; removes deviation D-1 | opus design; later than F |

## Trusted boundaries

- The port matches upstream. The proof covers the port, and a deviation check (stage S11) keeps the port equal to
  upstream apart from the deviations listed below. Every deviation is trusted to preserve behaviour.
- Deviations: D-1 the class drops its superclass `AbstractMutableList` and the `override` modifiers; D-2 `size`
  becomes a plain `var` with a private setter; D-3 `modCount` becomes a field of the port; D-4 the `AbstractList`
  index checks and `newCapacity` become private copies, verbatim; D-5 `@kotlin.internal.InlineOnly` is removed;
  D-6 the shared `emptyElementData` sentinel becomes a fresh `arrayOfNulls(0)`, and `elementData ===
  emptyElementData` becomes `elementData.size == 0`. The rewrite relies on the upstream invariant
  `elementData === emptyElementData ⇔ elementData.size == 0`: every assignment is the sentinel,
  `arrayOfNulls(n)` with `n > 0`, a non-empty `toTypedArray()` or a larger copy. An allocated empty deque is off
  the sentinel, so the invariant is about capacity, not `size`. It is argued by hand, not proved **[H]**. D-7 the members outside the deliverable are removed; D-8
  specification blocks and laws are added.
- Platform `copyInto` meets V2. Inspected: the JVM (`System.arraycopy`) and the JS `arrayCopy` overlap logic.
  The Wasm `array.copy` and Native `copyImpl` were followed to their intrinsics only. **[gap]**
- `Collection.toTypedArray()` returns a fresh array of exactly `size` elements (F only; the JVM calls
  `toArray(arrayOfNulls(0))`).
- Element `equals` is an equivalence relation (V5; docs/soundness.md, "Element equality in collections").
- The unchecked cast `as E` is covered by the existing type-argument assumption (docs/soundness.md,
  "Type invariants").
- The bundled ArrayDeque model is the ordinary trusted external spec (docs/soundness.md, "External
  specifications"). It is checked equal to the contracts that the port proves.

## Opus design decisions

- D0. Port first, conformance (V9) later. Recommendation: port. V9 would also have to trust `AbstractMutableList`'s
  inherited `iterator`, `subList`, `equals` and `hashCode` bodies. That is a larger and less local change.
- D1. Where the port lives: `testData/diagnostics/targets/arraydeque/`, with the vendored upstream file under
  `formver.compiler-plugin/testData/upstream/kotlin-2.3.0/` (Apache-2.0, notice kept). Recommendation as stated.
- D2. Form of the deviation check: a committed unified diff from the vendored upstream to the port, checked by
  `agent-scripts/check-testdata.sh`. Recommendation: one diff per file, with the specification blocks stripped by
  a fixed rule (leading `preconditions`/`postconditions`/`loopInvariants` blocks and `@Law` members) before the
  comparison.
- D3. Wrapping arithmetic: an opt-in function annotation (`@WrappingArithmetic`, recorded in the register) that
  encodes `+`, `-` and `*` modulo 2^32 inside that function. `newCapacity` uses only `+`, `-` and `shr 1`. The alternative is a precondition-strengthened copy
  of `newCapacity`, which would be a further deviation. Recommendation: the annotation. It keeps the
  upstream body verbatim. The wrapped encoding stays linear for `+`, `-` and multiplication by a constant;
  `*` of two variables is nonlinear under either encoding.
- D4. Whether V5 is acceptable. It makes executed equality agree with the relation the specifications already
  assume. A source `equals` that breaks the relation then gives a wrong proof in executed code too.
  Recommendation: accept, and add a register entry with an abusing test.
- D5. The shape of V6: which exits carry law obligations (every call that may throw, or only calls on shared or
  foreign receivers), and the golden churn across the suite. Recommendation: measure the churn on a branch
  before deciding.
- D6. Whether P2 (null hygiene) is a law or only a postcondition of the clearing operations. Recommendation:
  a law. `copyElements` needs it, and so does the indexOf regression.

## Stages

Each stage is one bounded, sol-sized change unless marked. Each lands with public-entry tests and a negative
sibling in the same file (docs/developing.md, "Negative tests").

| Stage | Change | Public-entry regression tests | Needs |
|---|---|---|---|
| S1 | V1 reads, writes and `size` for `Array<T>` | `stdlib/array/generic_array_elements.kt`: `swap`, `fillNulls` verify; `writeShared` and `readPastEnd` fail | — |
| S2 | V1 `arrayOfNulls`, `emptyArray`, null-hygiene quantifiers | `newArrayIsNull` verifies; `newArrayNonNull` fails | S1 |
| S3 | V2 `copyInto` and `fill` | `copy_into.kt`: `shiftLeftOverlap` and `shiftRightOverlap` verify against memmove semantics; `naiveForwardOverlap` (the claim a forward loop would give) fails; `copyPastEnd` fails | S1 |
| S4 | V3 `shr` | `halfOf(n)` verifies `n shr 1 == n / 2` for `n >= 0` and `(-3) shr 1 == -2`; `x shr 33 == x shr 1` verifies; a sibling claiming `(-3) shr 1 == -1` fails | — |
| S5 | V4 per D3 | the verbatim `newCapacity` verifies `result >= minCapacity`; the same body without the annotation reports `INTEGER_OVERFLOW` | D3 |
| S6 | V5 per D4 | `indexOfGeneric<E>` over an owned `Array<E>` verifies a `ListModel`-style postcondition; the abuse case in `soundness/equality.kt` is extended | D4 |
| S7 | Port skeleton: class, P1/P2 laws, constructors, mod helpers, `get`, `set`, `first`/`last`, `DequeSpec` | the port's members verify; `getWrapped` and `firstOnEmpty` siblings fail | S1–S4, D1, D6 |
| S8 | `ensureCapacity`, `copyElements`, `addFirst`/`addLast`/`add`, `removeFirst`/`removeLast` (+`OrNull`) | a client `rotate(d)` verifies through the port; `removeKeepsSlot` (a sibling that skips `elementData[head] = null`) fails P2 | S5, S7 |
| S9 | `indexOf`, `lastIndexOf`, `contains`, `clear`, `nullifyNonEmpty` | `indexOfPre80390` and `lastIndexOfPre80661` (the `indexOf` condition as `head >= tail`, the `lastIndexOf` condition as `head > tail`) fail their postconditions | S6, S8 |
| S10 | `external-specs/ArrayDeque.kt` and its bundled-vacuity entry | `bfs` client verifies through the bundled spec; a client `peekEmpty` fails its precondition | S9 |
| S11 | Vendored upstream file and the deviation check per D2 | the check fails when a port body differs from upstream outside the diff | D1, D2 |
| F1 | `add(index)`, `removeAt`, `remove(e)` | the port verifies; `addShiftOffByOne` fails | S11 |
| F2 | `removeRange` and its shift loops | the port verifies; a loop that stops after two segments fails | F1 |
| F3 (opus) | V6 and V8, then `filterInPlace` with P6 | the upstream `removeAll` fails P6(b) with a throwing `contains`; a variant whose exception cleanup restores the element under test, moves the unprocessed suffix to follow the kept prefix, nulls the vacated slots and sets `size` to their total, in both branches, verifies; a variant that only delays nulling fails P6(b) in the linear branch | D5 |
| F4 (opus) | V7, then `addAll`, `addAll(index)`, `ArrayDeque(Collection)` with P7 | the port verifies; `d.addAll(1, d)` in a client is an ownership error | F1 |
| F5 (opus) | The normal-exit content specification of `removeAll`/`retainAll` (kept elements are exactly those the predicate keeps) | `retainAllKeepsOrder` verifies; a sibling that reverses kept elements fails | F3 |

## Executable definition of value

The target delivers value when every check below passes on `main`. Names follow the stage table.

1. `./agent-scripts/test.sh --verify arraydeque` passes, and the port's in-scope members carry no
   `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE` or `UNTRACKED_WRITE`.
2. In the same goldens, `indexOfPre80390` and `lastIndexOfPre80661` fail with postcondition diagnostics, and the
   2.3.0 bodies verify. A proof that cannot tell the 2.3.0 code from the mutants delivers nothing.
3. `./agent-scripts/test.sh --vacuity arraydeque` lists nothing.
4. The deviation check of S11 passes, and the deviation list in this note equals the hunks of the committed
   diff.
5. `./agent-scripts/check-gradle-consumer.sh` passes with a client that uses `ArrayDeque` through the bundled spec,
   and the trust report lists the binding as `bundled spec`.
6. For F: the upstream `filterInPlace` fails P6(b) (the proof route to new defect 1) while it meets P6(a), and `d.addAll(1, d)` is
   rejected in a client.
7. Total `--verify` time for the port is reported in the land commit. Selection of F depends on it.

Reporting defect 1 and finding 2 upstream, from the drafts below, counts as value delivered whatever
maintainers decide.

## Maintenance cost

- Upstream drift: the pin is a release tag. When the pin moves, an upstream change shows up as a failure of the
  S11 check, and the cost is re-applying the diff. How often that happens is not measured **[H: rarely]**.
- Bundled spec: one more spec file in the vacuity check and the consumer check.
- Golden churn: V1–V5 add tests and should change few goldens **[H]**. V6 can change many outcomes across the
  suite **[H]**.
- Solver time: sequence updates, modular index arithmetic and two-segment quantifiers under Z3 4.8.7 **[H]**.
  The repo's default per-declaration timeout is the budget (README.md, `verification_timeout`).

## Risks

- The proof covers a port. The deviation check reduces the risk, and V9 removes it. Without V9, the deliverable
  does not prove that the shipped class meets its contracts.
- V5 and V4 widen trusted assumptions. Each needs a register entry and an abusing test.
- Quantifier instability across the wrap-around case split **[H]**. Mitigation: state the wrapped and unwrapped
  windows as separate lemma functions with explicit triggers.
- F3–F5 are research. F may stop at F2.
- Maintainers may close finding 2 "As Designed", the state of KT-55285. Value item 6 does not depend on their
  answer.

## Upstream contribution strategy

The strategy assumes no maintainer interest in SnaKt.

1. File the two drafts below as separate YouTrack issues. Nothing is filed; the drafts are not published.
2. Offer patches for the fixes the drafts name, with `ArrayDequeTest` cases that use the existing
   `testArrayDeque` layout enumeration.
3. If maintainers ask how the defects were found, describe the verification work. Do not propose specification
   annotations in the stdlib.
4. Publish the verified port and the mutation results as a SnaKt test and a short write-up. It is useful as an
   oracle for future ArrayDeque changes without any upstream adoption.

### Issue draft A: ArrayDeque.removeAll/retainAll corrupt the deque when `contains` throws

Observed on Kotlin 2.3.0 (kotlinc-jvm 2.3.0, JRE 21.0.10). **[E]**

> `ArrayDeque.removeAll` and `retainAll` leave the deque inconsistent when `elements.contains` throws. The size is
> unchanged, but elements are lost and others are duplicated or replaced by `null`. In the wrapped branch,
> `filterInPlace` nulls each slot before it calls the predicate; in the linear branch it overwrites slots as it
> compacts.
>
> `TreeSet.contains(null)` throws NPE under natural ordering:
>
> ```kotlin
> fun main() {
>     val d = ArrayDeque<Int?>(4)
>     d.addLast(0); d.addLast(0); d.removeFirst(); d.removeFirst()
>     d.addLast(1); d.addLast(2); d.addLast(null)    // [1, 2, null], wrapped
>     runCatching { d.removeAll(sortedSetOf(2)) }   // throws NPE
>     println("$d ${d.size}")                        // [1, null, null] 3
> }
> ```
>
> `arrayListOf(1, 2, null)` on the same call is left as `[1, null]`.
>
> With a non-null element type, `get` afterwards returns `null`:
>
> ```kotlin
> class Throwing(val limit: Int) : AbstractCollection<Int>() {
>     var calls = 0
>     override val size get() = 1
>     override fun iterator() = listOf(-1).iterator()
>     override fun contains(element: Int): Boolean {
>         if (++calls > limit) throw IllegalStateException("boom")
>         return element % 2 == 0
>     }
> }
>
> fun main() {
>     val d = ArrayDeque<Int>(8)
>     repeat(5) { d.addLast(0) }; repeat(5) { d.removeFirst() }   // head 5
>     (5..11).forEach { d.addLast(it) }                           // [5, 6, 7, 8, 9, 10, 11], wrapped
>     runCatching { d.removeAll(Throwing(2)) }
>     println("$d ${d.size}")                    // [5, null, null, 8, 9, 10, 11] 7
>     runCatching { d.sumOf { it } }.onFailure { println(it) }   // NullPointerException
>
>     val e = ArrayDeque<Int>(16).apply { addAll(0..7) }   // linear
>     runCatching { e.removeAll(Throwing(3)) }
>     println(e)                                 // [1, 1, 2, 3, 4, 5, 6, 7]
> }
> ```
>
> On the same inputs `java.util.ArrayDeque.removeAll` keeps its contents, and `java.util.ArrayList.removeAll`
> leaves a consistent partial result.
>
> Expected: the deque holds only elements that were added, and `size` counts them. A partial removal is
> acceptable. Suggested fix, in both branches of `filterInPlace`: when the predicate throws, keep the element
> under test, move the unprocessed suffix to follow the kept prefix, null the vacated slots and set `size` to the
> kept count plus the suffix length, as the `finally` block of `java.util.ArrayList.batchRemove` does. Delaying the
> nulling alone is not enough: the linear branch already delays it and still duplicates elements.

### Issue draft B: ArrayDeque.addAll(index, elements) with `elements === this` inserts shifted elements

Observed on Kotlin 2.3.0 (kotlinc-jvm 2.3.0, JRE 21.0.10). **[E]**

> `addAll(index, elements)` shifts the buffer before it iterates `elements`. When `elements` is the deque itself,
> the iterator reads the shifted buffer.
>
> ```kotlin
> fun main() {
>     val d = ArrayDeque(listOf(0, 1, 2, 3))
>     d.addAll(1, d)
>     println(d)    // [0, 0, 0, 0, 0, 1, 2, 3]
>     val e = ArrayDeque(listOf(0, 1, 2, 3))
>     e.addAll(3, e)
>     println(e)    // [0, 1, 2, 0, 1, 2, 0, 3]
> }
> ```
>
> `ArrayList(listOf(0, 1, 2, 3)).apply { addAll(1, this) }` gives `[0, 0, 1, 2, 3, 1, 2, 3]`. `addAll(d)`
> (append) is correct.
>
> Request: snapshot `elements` when it is the deque itself, as `ArrayList` does, or document that passing the deque
> to its own `addAll(index, ...)` is unsupported.

## AndroidX `CircularIntArray`

`androidx.collection.CircularIntArray` (`collection/collection/src/commonMain/kotlin/androidx/collection/CircularIntArray.kt`
on `androidx-main`, read for this note) stores `IntArray elements`, `head`, `tail` and `capacityBitmask`. Capacity
is a power of two, and indices wrap with `and capacityBitmask`. **[E]** It is a poor stepping stone:

- It needs bitwise `and` with power-of-two reasoning, which ArrayDeque does not need.
- It skips what is hard about ArrayDeque: generic storage, element equality, middle insertion and removal, and
  the `MutableList` refinement.
- Its `IntArray` storage is already supported. A proof of it would show little new.

What transfers (a wrap-around window law, a copy on growth) is covered by S3 and S7. Use it only if bitwise `and`
is wanted for other reasons.

## Selection against the alternatives

- Persistent collections (kotlinx.collections.immutable): trie and HAMT structures with structural sharing. Their
  nodes are shared immutable data, which fits SnaKt's `val` and `@Pure` recursion rather than its unique mutable
  state. Proofs are deeper and recursive. The upstream bug evidence is unknown to this note.
- Ktor ranges: small arithmetic functions. Cheap, with high odds of success, and few verifier changes.
- ArrayDeque sits between the two. It has known upstream defects (three, one of them new here, plus one
  unspecified aliasing divergence), wide deployment
  across platforms, and verifier changes (V1–V5) that every array-backed container needs. Its main weakness is
  the port boundary.

Select ArrayDeque M if the alternatives cannot show comparable defect evidence. File drafts A and B whichever
target is selected.

## Evidence gaps

- Usage counts outside the Kotlin repo.
- Whether F1/F2 would find any functional bug that the layout tests miss.
- Wasm `array.copy` and Native `copyImpl` overlap semantics, beyond their documented contract.
- Whether D-6's sentinel equivalence holds on every path. It is argued, not checked.
- Solver times for S7–S9. Nothing has been run through SnaKt.
- Whether a generic class whose fields are `Array<Any?>` and whose laws quantify over them converts at all
  before S1. This note assumes it does not.
- The maintainers' view of drafts A and B. Neither is filed.
