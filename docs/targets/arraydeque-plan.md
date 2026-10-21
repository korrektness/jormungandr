# Plan: Kotlin stdlib `ArrayDeque`

Use this plan when building or reviewing the ArrayDeque target. It owns the selection, the design decisions, the
stages, the acceptance checks and what stays trusted. `docs/targets/kotlin-arraydeque.md` (the note) owns the
evidence, the upstream pin and the scope of the deliverables M and F; this plan cites its labels (P1–P7, V1–V9).

The language reference in `SPECIFICATIONS.md` records supported verifier features.
S1's generic-array element model is implemented. S2's disjoint copying and fill remain planned.

## Selection

ArrayDeque is the target. This is the engineering direction, not an approval of every design idea in the note. The
target's own merits are in the note's "Value" and "Deployment relevance". Against the alternatives:

- Ktor ranges (`docs/targets/ktor-ranges.md`): the deviations that matter are in the parser and the plugin policy,
  and ordinary unit tests find and fix them. A proof adds little.
- Persistent collections (`docs/targets/persistent-collections.md`): every bug of interest is an exceptional-exit
  bug, and the ordered builders' guarantees rest on the hash-map builder, which does not meet them at the pin. The
  smallest useful proof needs V6 first.

## Minimum target

The minimum target is reached when all of the following hold on `main`:

1. A port of the upstream class in `testData/diagnostics/targets/arraydeque/` verifies. In scope: the members of the
   note's deliverable M and the private helpers they call. No in-scope declaration carries
   `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE`, `INTERNAL_ERROR` or
   `UNTRACKED_WRITE`, and none is excluded from verification.
2. The two mutants fail, each with a postcondition diagnostic on the clause named under S8, and the upstream bodies
   verify. The deviation checker of S5 shows that each mutant differs from the port only in the guard of its wrapped branch.
3. `./agent-scripts/test.sh --vacuity` lists nothing for the port's file or the consumer's file.
4. The deviation check passes. The committed diff is the only list of deviations; each hunk carries its D-label.
5. The bundled model `external-specs/ArrayDeque.kt` has contracts equal to the port's interface `DequeSpec`, checked
   by a test.
6. A real consumer verifies through the bundled model, both in testData and in the client project of
   `./agent-scripts/check-gradle-consumer.sh`, whose trust report lists the binding as a bundled spec.
7. A provenance file next to the vendored source records the note's "Pin" (tag, commit, file hash, license), with
   `license/LICENSE.txt` and `license/NOTICE.txt` copied, and the residual trust labels from "D2, D-6 and residual trust".
8. The land commit of each verifying stage reports its `--verify` wall time per test file.

The consumer is a breadth-first search over a graph in compressed-sparse-row form (`IntArray` offsets and targets),
with an `ArrayDeque<Int>` worklist and an `IntArray` parent map. It is the shape of `PureRecursion.cycle` with the
`Map`, `Set` and `generateSequence` uses replaced by arrays; `cycle` itself needs `ArrayDeque(Collection)` and
`generateSequence`, which are out of scope. Claims: every vertex with a parent is reachable from the start along
parent links, and when the loop ends every successor of a visited vertex is visited. A second function,
`enqueueOnce`, adds a vertex only when `!queue.contains(v)` and proves it is then `last()`. Negative siblings: a loop
bound that skips the last successor fails the closure claim, and `enqueueOnce` claiming `size` grew without the
`contains` test fails.

## Design decisions

### V1: array element model

Decision:

- Model `Array<T>` like `IntArray`: a unique array's predicate holds a `Seq[Ref]`; reads and writes need a unique
  path; a shared read gives an arbitrary value and a shared write is dropped (docs/soundness.md, "Arrays"). A read
  inhales the static element type, as any read does. The register entry "Type invariants" already covers the gap
  this leaves (an array created through an unchecked cast). No new register entry. An `UNSOUND` case for
  `arrayOfNulls<Any?>(1) as Array<String>` joins `verification/soundness/erased_generics.kt`.
- `arrayOfNulls(n)` gives `n` nulls, `emptyArray()` gives size 0, and `lastIndex` is `size - 1`. `Array(n) { }` and
  `arrayOf(...)` are out of scope.
- The port's storage is `Array<Any?>`, so the model gives the port no element typing. The only typed step is
  `elementData[i] as E` in `internalGet`, an erased no-op covered by "Type invariants". The port writes only `E`
  values and `null`, so the assumption holds for the port's own writes.
- P2 (null hygiene) is a class law, not only a postcondition of the clearing operations. The mutants do
  not need it: without P2 a cleared buffer may hold a slot equal to the argument, so the verifier rejects the
  KT-80390 mutant on `result < size` either way. P2 is in scope because it is
  an upstream guarantee in its own right (removed elements are not retained), and `removeKeepsSlot` tests it. State
  P2 through a `@Pure` helper that maps a buffer index to its window offset, so the quantifier triggers on
  `elementData[j]`. Its cost on every member is measured in S6 and S7.

### V2: `copyInto`, aliasing and permissions

The minimum target needs only distinct arrays. In M, `copyInto` appears only in `copyElements`, which copies into a
fresh `arrayOfNulls`. Every same-array call is in `add(index)`, `addAll(index)`, `removeAt` and `removeRange`, all
outside M. Decision: split V2.

- V2a (in M): `src.copyInto(dst, off, from, to)` where `src` and `dst` are different unique paths, and
  `fill(v, from, to)` on a unique array. Two different unique paths cannot alias under the ownership discipline, so
  disjointness needs no proof. The effect is `dst' = dst[..off] ++ src[from..to] ++ dst[off+len..]`; every bound
  is an obligation. A call whose receiver and destination are the same path is `UNSUPPORTED_FEATURE` in M, with a
  test, so it cannot be read as a disjoint copy.
- V2b (out of M): the same path as receiver and destination, lowered with one permission to the memmove form
  `a' = a[..off] ++ old(a)[from..to] ++ a[off+len..]`. The KDoc allows overlap but does not state the result; the
  memmove reading is then a trusted interpretation, matched by the JVM `System.arraycopy` and JS `arrayCopy`
  contracts, and only followed to intrinsics for Wasm (`array.copy`) and Native (`copyImpl`). That trust enters only
  with V2b.

For distinct arrays every platform's semantics is a plain copy, so M trusts only the documented `copyInto` contract.

### D3: wrapping arithmetic

Kotlin `Int` arithmetic wraps. SnaKt's overflow obligation is stricter than the language, so encoding wrap-around
exactly is a precision change, not a new assumption. The cost is in the verifier's own encoding.

A range precondition on `newCapacity`, the alternative, does not work for the port. `newCapacity` overflows
only for `oldCapacity > 1431655764`. A precondition excluding that needs a capacity bound that `ensureCapacity`
cannot maintain, because growth multiplies the capacity by 1.5; maintaining it changes `ensureCapacity`, a body
deviation. There is a second site: `registerModification` does `modCount += 1`, which overflows after
2^31 modifications and which no precondition can rule out.

Decision: an opt-in function annotation that gives `+` and `-` on `Int` their two's-complement meaning inside the
annotated function. Both operands are in range, so the result needs one conditional correction by 2^32, which stays
linear. `*`, `/` and `%` keep the overflow obligation in an annotated function, and the annotation does not reach
callees. The trust report lists each annotated function, because a
reader of a proof must know that its overflow checks are off there. No register entry: nothing is assumed.

Applied to: `newCapacity` and `registerModification`. Neither body changes. Tests: the verbatim `newCapacity`
verifies `result >= minCapacity` for non-negative arguments; the same body unannotated reports `INTEGER_OVERFLOW`;
an annotated claim `result <= Int.MAX_VALUE - 8` fails (the `minCapacity > maxArraySize` branch returns
`Int.MAX_VALUE`); `Int.MAX_VALUE + 1 == Int.MIN_VALUE` verifies in an annotated function.

V3 lands in the same stage, because `newCapacity` is its only M use: `x shr c` for a constant `c` is
`floorDiv(x, 2^(c and 31))`, the arithmetic shift with Kotlin's masked distance. `ushr` and `and` stay unmodelled;
each needs its own unsigned or bitwise encoding.

### D4 and V5: element equality

A global V5 is rejected: one where every executed `==` of the arbitrary kind returns the specification relation,
everywhere. That equates arbitrary executable `equals` with an equivalence relation across all code.

What the port needs is narrower. In `indexOf`, `lastIndexOf` and `contains`, the result depends on executed
`element == elementData[i]`, whose static type is the unbounded `E`; today that is an arbitrary Boolean. The
postconditions (the `ListModel.indexOf` form) compare with the specification relation `EqualsRelation`, which the
register entry "Element equality in collections" already assumes is an equivalence relation.

Decision: a function annotation, registered, that links the two inside the annotated function only. In such a
function an executed `==` of the arbitrary kind may still throw, and on normal return its result is
`EqualsRelation(l, r)`. Nothing changes elsewhere. The annotation goes on exactly `indexOf` and `lastIndexOf`;
`contains` calls `indexOf` and needs none. The trust report lists each use; a new register entry "Executed element equality"
names it with tests.

What stays trusted, for these functions only:

- each executed `equals` call is deterministic and agrees with the specification relation;
- the call does not reach the deque (the existing "Unique borrows" entry; a hostile `equals` holding an alias is
  outside the ownership discipline already);
- the equivalence axioms of the relation (the existing entry; not new).

What a hostile `equals` can do: one that is not reflexive, not deterministic or not consistent makes the real
`indexOf`/`contains` return what the contract excludes, for example `contains(x)` false right after `addLast(x)`. A
client proof built on that contract can then be wrong at run time. It cannot break P1, P2 or `size`: the search
members do not write, so the laws hold whatever `equals` returns. A throwing `equals` gives an exceptional exit, on
which nothing is claimed. For `E` of `Int`, `String`, an enum or an identity class, the language fixes `equals`, so
the assumption is true and no residual trust remains; the consumer uses `Int`.

Tests in S4, on a stand-alone generic search over an owned `Array<E>`: the annotated search verifies the
`ListModel.indexOf` postcondition; the same body without the annotation still verifies `result >= -1 && result <
size` and fails only the equality clauses, which shows which clauses rest on the assumption; an un-annotated caller of an annotated function gets
only the callee's contract; `soundness/equality.kt` gains an `UNSOUND` case with a non-reflexive `equals` in an
annotated search that verifies a false `contains`. S8 repeats the un-annotated sibling on the port's `indexOf`.

Both mutants are detectable without this decision (note, "Regression cases KT-80390 and KT-80661"). The mutants
still carry the annotation, so that they differ from the upstream body only in the guard.

### D2, D-6 and residual trust: port correspondence

- D0: the proof targets a port; conformance (V9) would also have to trust `AbstractMutableList`'s inherited bodies.
- D1: the port lives in `testData/diagnostics/targets/arraydeque/`; the vendored file goes to `formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/`,
  outside `diagnostics/` so that test generation does not pick it up, with `LICENSE.txt`, `NOTICE.txt` and the
  provenance file.
- D2: the check is a JUnit test in the compiler-plugin suite, so the land gate runs it. It strips SnaKt
  specification syntax by a fixed rule, diffs the vendored file against the port, and compares the result with the
  committed `ArrayDeque.port.diff`. The rule removes: leading `preconditions`/`postconditions` blocks,
  `loopInvariants` blocks, `verify(...)` statements, `@Law` and `@SpecOf` members, and the annotations `@Pure`,
  `@Unique`, `@Borrowed` and the two annotations of D3 and D4. It removes nothing else. Specification syntax has no
  effect on execution, so stripping it cannot hide a body change. Tests: a fixture whose port changes one body
  expression fails the check; a fixture that only adds a specification block passes.
- The deviation list lives in the committed diff, one D-label per hunk, not in prose. D-1 has knock-on hunks: the port has no `Collection` supertype, so `isNotEmpty()` and the `AbstractList` index checks
  need private copies. The diff records each.
- D-6 is checked, not argued. Upstream assigns `elementData` at six sites; four are in M (the `(Int)` constructor,
  `()`, `ensureCapacity`'s first allocation, `copyElements`). The port carries a `verify` at each: the sentinel
  sites assign a size-0 array, and the others assign `size > 0` (`ensureCapacity` allocates at least 10;
  `copyElements` allocates `newCapacity >= minCapacity > old size`, from the S3 postcondition). With the diff check
  pinning the set of assignment sites, this proves `elementData === emptyElementData ⇔ elementData.size == 0` for
  the port's histories, which is what the rewritten test needs.
- Residual trust, labelled in the provenance file: the stripped diff is behaviour-preserving for its listed hunks
  (D-1 superclass removal, D-2 `size` as a plain `var`, D-3 `modCount` as a field, D-4 private copies of
  `AbstractList` helpers, D-5 `@InlineOnly` removal, D-6 the shared empty sentinel replaced by `arrayOfNulls(0)` and
  `elementData === emptyElementData` by `elementData.size == 0`, D-7 removed members, D-8 added specification). The proof is of the port,
  not of the shipped class.

### Bundled model shape

Concrete library classes have no bundled model yet; only interfaces are bound. Decision: `ArrayDequeModel<E>`
declares only the in-scope members and does not extend `MutableListModel`, so an out-of-scope member called on an
`ArrayDeque` receiver is an opaque library call and not a contract the port never proved. If the binding rules
require the supertype model, the inherited members are listed in the trust report as bound to the `MutableList`
model, not proved by the port. Whether constructors of a bound class can be modelled is unchecked **[H]**; S9
starts by checking it, and if not, a constructor-binding change is its own stage before S9.

## Stages

Each stage lands separately with public-entry regression tests and a negative sibling in the same file, runs the
fast loop on what it touched, and runs `check-all.sh` when it touches shared verifier code. Sizes are estimates of
the diff including tests.

| Stage | Change | Acceptance | Size |
|---|---|---|---|
| S1 | V1: `Array<T>` reads, writes, `size`, `lastIndex`, `arrayOfNulls`, `emptyArray` | `stdlib/array/generic_array_elements.kt`: `swap`, `fillNulls`, `newArrayIsNull` verify; `writeShared`, `readPastEnd`, `newArrayNonNull` fail; the `UNSOUND` cast case verifies | 500–700 |
| S2 | V2a: disjoint `copyInto`, `fill` | `copyGrow` (the `copyElements` shape) verifies; `copyPastEnd` fails; `selfCopy` reports `UNSUPPORTED_FEATURE` | 400–600 |
| S3 | V3 constant `shr`, D3 annotation, `coerceAtLeast` model | the D3 tests; `(-3) shr 1 == -2` verifies, `== -1` fails | 400–600 |
| S4 | D4 annotation and register entry | the D4 tests | 300–500 |
| S5 | Vendored upstream, provenance, deviation checker with fixtures | checker fixtures pass and fail as stated in D2 | 300–500 plus the vendored file |
| S6 | Port skeleton: class, P1/P2 laws, constructors, mod helpers, `get`, `set`, `first`/`last` (+`OrNull`), `DequeSpec` | members verify; `getWrapped` (no `positiveMod`) and `firstOnEmpty` fail; deviation check passes | 400–600 |
| S7 | `ensureCapacity`, `copyElements`, `registerModification`, `addFirst`/`addLast`/`add`, `removeFirst`/`removeLast` (+`OrNull`) | a client `rotate(d)` verifies through the port; `removeKeepsSlot` fails P2; D-6 `verify`s hold | 400–700 |
| S8 | `indexOf`, `lastIndexOf`, `contains`, `clear`, `nullifyNonEmpty`; mutants | upstream bodies verify; `indexOfPre80390` fails on `result < size`; `lastIndexOfPre80661` fails on `(result == -1) == !contains(element)`; vacuity clean on the port | 300–500 |
| S9 | `external-specs/ArrayDeque.kt`, bundled-vacuity entry, spec-equality test, consumer in testData and in the Gradle consumer project | the consumer claims and negatives above; `peekEmpty` fails its precondition; `check-gradle-consumer.sh` passes with the trust report entry | 500–800 |
| S10 | Upstream patch artifact for defect 1 | `docs/targets/arraydeque/filterInPlace.patch` plus `ArrayDequeTest` cases using `testArrayDeque`'s layout enumeration with a throwing `contains` at every position; the cases fail on 2.3.0 and pass patched, run against a scratch build and reported in the commit | 200–400 |

Order: S1 → S2; S1 → S4; S3 and S5 are independent of everything else; S6 needs S1, S3, S5; S7 needs S2, S6; S8 needs
S4, S7; S9 needs S8. S10 is independent of all verifier work. No stage touches two verifier subsystems.

Stop rule: if S6 or S7 cannot verify a member within the default per-declaration timeout after splitting the
wrapped and unwrapped windows into lemma functions (note, "Risks"), stop and report the timings before S8. Do not
raise the timeout to pass.

## Out of scope

- The note's deliverable F and its stages F1–F5.
- V2b (same-array `copyInto`), V6, V7, V8, V9.
- What the note's "Proof properties" lists as not claimed.
- Filing issue drafts A or B, or offering the patch upstream. Finding 2 (`addAll(index, this)`) is unspecified
  aliasing and is never presented as a defect.

## Deciding on exceptional exits

After S9 a separate decision chooses between proving the patched `removeAll`/`retainAll` (F3: V6 and V8 first) and
ending at the minimum target plus the S10 patch. Prove it only if all of these hold:

1. Headroom: every port declaration verifies in under half the default per-declaration timeout, so that adding law
   obligations on exceptional exits does not push the existing members over.
2. Churn: on a branch, enabling law obligations at the exceptional exits of members that borrow a unique
   receiver changes the outcome of at most 5% of the suite's verified functions, and each change is a true finding
   or is fixed by a specification edit of a few lines.
3. A second customer: at least one other planned target needs V6 (the persistent-collections ordered builders
   qualify if that target is selected). ArrayDeque alone does not justify V6: defect 1 is already reproduced and
   the S10 tests already pin the fix.
4. Marginal value over S10: the S10 tests enumerate every layout up to capacity 15 and every throw position. The
   proof is justified only if it covers something those tests cannot, for example a capacity-independent claim
   that a reviewer asks for.
5. Feasibility: a stand-alone V8 spike proves P6(b) for a small array compaction loop with one throwing call, within
   the default timeout.

If any fails, the target ends at the minimum plus the patch, and F3 is recorded as not justified with the measured
numbers.

## What stays trusted

At the end of the minimum target:

For port correspondence, see ‘D2, D-6 and residual trust: port correspondence’; for array and cast assumptions, see V1 and V2; for equality assumptions, see D4.
- The bundled ArrayDeque model, as for any external spec, except that a test checks it equals the proved
  `DequeSpec` contracts.
- Nothing on exceptional exits.
