# Plan: Kotlin stdlib `ArrayDeque`

Use this plan when building or reviewing the ArrayDeque target. It owns the selection, the design decisions, the
stages, the acceptance checks and what stays trusted. `docs/targets/kotlin-arraydeque.md` (the note) owns the
evidence, the upstream pin and the scope of the deliverables M and F; this plan cites its labels (P1–P7, V1–V9).

The language reference in `SPECIFICATIONS.md` records supported verifier features.
S1, S2, S3 and S10 are implemented. "Feasibility" records what the port needs beyond them and which claims it
restricts.

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
5. The bundled model `external-specs/ArrayDeque.kt` matches the public contract projection of `DequeSpec` defined
   under ‘Bundled model shape’, checked by a test.
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
`contains` test fails. `docs/targets/arraydeque-bfs-design.md` owns the consumer's invariants, its verifier
prerequisite and its landing split.

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
- P2 (null hygiene) is a postcondition of each member that vacates slots (`removeFirst`, `removeLast`, `clear`):
  the vacated slots hold `null` on return. It is not a class law, because a class law cannot read array elements
  (see "Feasibility", G6). The mutants do not need it: without P2 a cleared buffer may hold a slot equal to the
  argument, so the verifier rejects the KT-80390 mutant on `result < size` either way. P2 is in scope because it is
  an upstream guarantee in its own right (removed elements are not retained), and `removeKeepsSlot` tests it.

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
everywhere. That makes every arbitrary executable `equals` a deterministic function of its operands across all
code.

What the port needs is narrower. In `indexOf`, `lastIndexOf` and `contains`, the result depends on executed
`element == elementData[i]`, whose static type is the unbounded `E`; today that is an arbitrary Boolean. The
postconditions (the `ListModel.indexOf` form) compare with the specification relation `EqualsRelation`. The
`EqualsRelation` domain fixes the relation only for a null left operand, value types and identity classes; it has
no reflexivity, symmetry or transitivity axiom, so `x == x` on an unbounded `E` does not verify.

Decision: a function annotation `@TrustedElementEquality`, a trusted assumption, that links the two inside the
annotated function only.

- Scope: an executed `==`, `!=` or `when`-subject comparison of the `Dynamic` kind in
  `conversion/KotlinEquality.kt` whose left operand's static type is a type parameter, nullable or not, in the
  body of the annotated function. An inlined callee keeps its own mode, as for D3. `Value`, `Identity`,
  `Structural` and `Declared` kinds are unchanged, and so is a `Dynamic` comparison whose left operand is an
  interface, open class or library type.
- Encoding: the executed form keeps its nondeterministic exceptional exit when a catch is active, and its value is
  `EqualsRelation(l, r)` instead of a fresh Boolean. No axiom is added to the domain. The `ListModel.indexOf`
  postconditions need none: each clause compares `element` with a stored element, and the executed comparison
  supplies exactly that relation value.
- Registration: unlike `@WrappingIntArithmetic`, which is a semantic opt-in, each annotated function is a
  `TrustedAssumption` in the trust report. The register entry "Executed element equality" in `docs/soundness.md`
  names the annotation and its tests.
- Use: exactly `indexOf` and `lastIndexOf`; `contains` calls `indexOf` and needs none. The ownership check of
  `convertEquality` does not apply: `element` is a shared parameter and `elementData[i]` a shared element.

What stays trusted, for these functions only:

- each executed `equals` call on the element type agrees with the specification relation, which makes it
  deterministic;
- the call does not reach the deque (the existing "Unique borrows" entry; a hostile `equals` holding an alias is
  outside the ownership discipline already).

What a hostile `equals` can do: one whose result changes between calls on the same operands makes the real
`indexOf`/`contains` return what the contract excludes, for example `indexOf(x)` returning `-1` right after a call
that returned `0`, while the contract fixes both results by the same relation value. A client proof built on that contract can then be wrong at run time. It cannot break P1, P2 or `size`: the search
members do not write, so the laws hold whatever `equals` returns. A throwing `equals` gives an exceptional exit, on
which nothing is claimed. For `E` of `Int`, `String`, an enum or an identity class, the language fixes `equals`, so
the assumption is true and no residual trust remains; the consumer uses `Int`.

Tests in S4, on a stand-alone generic search over an owned `Array<E>`: the annotated search verifies the
`ListModel.indexOf` postcondition; the same body without the annotation still verifies `result >= -1 && result <
size` and fails only the equality clauses, which shows which clauses rest on the assumption; an un-annotated caller of an annotated function gets
only the callee's contract; `soundness/equality.kt` gains an `UNSOUND` case with an `equals` that alternates its
result, where two calls of an annotated search verify equal results that differ at run time. S8 repeats the un-annotated sibling on the port's `indexOf`.

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
  effect on execution under the syntactic trust assumptions in `testData/upstream/kotlin-v2.3.0/PROVENANCE.md`.
  Tests: a fixture whose port changes one body expression fails the check; a fixture that only adds a specification block passes.
- The deviation list lives in the committed diff, one D-label per hunk, not in prose. D-1 has knock-on hunks: the port has no `Collection` supertype, so `isNotEmpty()` and the `AbstractList` index checks
  need private copies. The diff records each.
- D-6 has a syntactic assignment-site guard. Upstream assigns `elementData` at six sites; four are in M (the `(Int)` constructor,
  `()`, `ensureCapacity`'s first allocation, `copyElements`). The port carries a `verify` at each: the sentinel
  sites assign a size-0 array, and the others assign `size > 0` (`ensureCapacity` allocates at least 10;
  `copyElements` allocates `newCapacity >= minCapacity > old size`, from the S3 postcondition). With the diff check
  pinning direct reference-assignment sites, the intended invariant is `elementData === emptyElementData ⇔ elementData.size == 0`
  for the port's histories. The guard's scope and residual trust are recorded in the provenance file; it does not establish equivalence.
- Residual trust and proof scope are recorded in `formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/PROVENANCE.md`.

### Bundled model shape

`ArrayDequeModel<E>` declares only the in-scope members and does not extend `MutableListModel`, so an
out-of-scope member called on an `ArrayDeque` receiver is rejected, not given a contract the port never proved. `ArrayDeque()` binds through a
constructor model (S9a); `docs/targets/arraydeque-ctor-binding.md` owns the constructor binding, the unbound-member
policy and their fallbacks.

`DequeSpec` has two observers: `size` and `elementAt(index)`, the element at a window offset. The port implements
`elementAt` with a `@Pure` body that reads `elementData` (D-8), which needs G2. Upstream `get` throws on a bad index,
so it cannot be a `@Pure` observer in the port; the port proves that `get(i)` returns `elementAt(i)` and changes
nothing. The model declares `get` as the observer, as `ListModel` does, and the spec-equality test of S9 compares
the model's `get` with the port's `elementAt` under that mapping, and the model's mutator contracts with the
port's. The law projection retains nonnegative size; the port's upper size invariant is excluded.

## Feasibility

A spike of a reduced port (P1 law, wrap-around helpers, `get`, `addLast`, `removeFirst`, `removeLast`,
`copyElements`, a three-loop `indexOf`) checks what the M build needs. Each spike file verified in 12–17 s wall
time including Gradle start-up, and no declaration came near the 300 s per-declaration timeout. The one exception
was a law using `%`, which timed out in its helper; the port states offsets with conditional subtraction, never `%`.
The landed foundations verify in 10–12 s wall per file (`generic_array_elements`, `generic_array_bulk`,
`wrapping_arithmetic`, `int_shift`, `int_coerce_at_least`). The quantifier-cost risk of the note is therefore
not the constraint. The constraints are these gaps:

- G1: `===` is rejected when both operands may hold a boxed value, and a source class's specification has no
  exemption. Every element-preservation clause of the port (`get(i) === elementAt(i)`, the frame of `addLast`, the
  result of `removeFirst`) compares `E` or `Any?` operands, and the `==` fallback does not verify because
  `EqualsRelation` is not reflexive. Fix (S5a): accept `===` inside specification blocks (`preconditions`,
  `postconditions`, `loopInvariants`, `verify`, laws), where it compares model references; injected values are
  one-to-one in `Ref`, so for them it means equal values. Executed `===` and `@Pure` bodies keep the rejection,
  so no executed code observes runtime box identity. This states no new assumption; SPECIFICATIONS.md gains the
  meaning and the register entry "Boxed identity" its scope.
- G2: a `@Pure` function with a `@Unique` receiver or parameter must return a value type, so the port cannot
  implement `elementAt`. Elements of a unique `Array<T>` are shared values (V1), so returning one does not alias
  owned data. Fix (S5b): the purity check accepts a non-value result that is an element read of a unique array, or
  an unchecked cast of one.
- G3: a `var` cannot be a `@Pure` observer. Restriction instead of a fix: the port keeps `size` in a private `var`
  and exposes `@Pure @get:Unique override val size` reading it (D-2). An `@Pure override var` currently reports
  `INVALID_OBSERVER` and an `INTERNAL_ERROR` (`ClassCastException`, `BackingFieldGetter` to `CustomGetter`) on every
  member of the class; S5b replaces the internal error with the `INVALID_OBSERVER` diagnostic alone.
- G6: a class law cannot read array elements ("Non-value read or call in law"). Restriction: P2 is a postcondition
  of the vacating members (V1), not a law.
- G7: a postcondition that indexes a unique array field with a `@Pure` member call of the same receiver does not
  verify even against the identical returned expression (`return elementData[positiveMod(head + index)]` with
  `result === elementData[positiveMod(head + index)]`); the same comparison in a `verify` inside the body does.
  The contract nests the call inside `unfolding` of the receiver predicate. G8: the same call inside a `forAll` in
  the postcondition of a member that reassigns `elementData` reports `OWNERSHIP_NOT_ESTABLISHED`. Fix (S5c):
  reproduce both as tests, then encode such calls outside the unfolding. Stating contracts through `elementAt` may
  avoid both; S5c checks that first and keeps the tests either way.
- G9–G12: a constructor that assigns owned storage from a `when` with a `throw` arm is skipped; a call on an
  instantiated generic source class gets no member contract; the correspondence check parses diagnostic markup;
  a `preconditions` block in a secondary constructor is dropped. Fixes (S5d, S5e, S5f, S5g):
  `docs/targets/arraydeque-gaps-design.md`, which owns their designs and tests. None adds a trusted assumption.

Claim restrictions:

- Capacity: `internalIndex` computes `head + index` with `head < capacity` and `index < size <= capacity`, which
  exceeds `Int.MAX_VALUE` once the capacity is above 2^30. The port carries a law `elementData.size <= 2^30`, a
  precondition `initialCapacity <= 2^30` on the `(Int)` constructor, and `size < 715827882` on `addFirst`,
  `addLast` and `add` (growth from a full buffer of that size stays at or below 2^30). `DequeSpec` and the model carry the
  preconditions as caller obligations, not library-wide laws. Clients verified through this model make claims
  about deques under these growth bounds. Nothing is claimed for larger deques. Upstream at such sizes is not
  reproduced and not claimed to be defective.
- Element equality: element-preservation clauses state reference identity (G1); search clauses state
  `EqualsRelation` (D4).
- P2 per operation, not as a law (G6).

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
| S5a | G1: `===` in specification blocks | in a source class with `Array<Any?>` storage, `get(i) === elementAt(i)` verifies; executed `===` on `Any?` operands and `===` in a `@Pure` body stay `UNSUPPORTED_FEATURE`; a false `===` claim on two different stored references fails | 200–400 |
| S5b | G2: element results of `@Pure` unique readers; G3 internal error | `elementAt` over a unique `Array<Any?>` field verifies as an observer of an interface; a `@Pure` reader returning a unique field's object stays `PURITY_VIOLATION`; `@Pure override var` reports `INVALID_OBSERVER` only | 200–400 |
| S5c | G7, G8: `@Pure` member calls in contracts | the identical-expression postcondition and the quantified frame after reassigning `elementData` verify; a false frame claim fails | 200–500 |
| S5d | G9: arms that end abnormally in moves of fresh unique values | the tests of G9 in `arraydeque-gaps-design.md` | 150–300 |
| S5e | G10: contracts of use-site substitution overrides | the tests of G10 in `arraydeque-gaps-design.md` | 150–300 |
| S5f | G11: markup clearing in the correspondence check | the tests of G11 in `arraydeque-gaps-design.md` | 50–150 |
| S5g | G12: preconditions of secondary constructors | the tests of G12 in `arraydeque-gaps-design.md` | 200–400 |
| S6 | Port skeleton: class, P1 law and capacity law, constructors, mod helpers, `get`, `set`, `first`/`last` (+`OrNull`), `DequeSpec` with `size` and `elementAt` | members verify; `getWrapped` (no `positiveMod`) and `firstOnEmpty` fail; deviation check passes | 400–600 |
| S7 | `ensureCapacity`, `copyElements`, `registerModification`, `addFirst`/`addLast`/`add`, `removeFirst`/`removeLast` (+`OrNull`), capacity preconditions | clients `rotateLeft` (`removeFirst`, then `addLast`) and `rotateRight` (`removeLast`, then `addFirst`) verify the rotated sequence through the port, and a sibling claiming the unrotated sequence fails; `newCapacity` without `@WrappingIntArithmetic` reports `INTEGER_OVERFLOW`; a `copyElements` whose second `copyInto` copies one element too many fails its bounds obligation; `ArrayDeque.port.diff` carries no proof-lemma hunk; `removeKeepsSlot` fails the P2 postcondition; `addLast` without the capacity precondition reports `INTEGER_OVERFLOW`; D-6 `verify`s hold | 400–700 |
| S8 | `indexOf`, `lastIndexOf`, `contains`, `clear`, `nullifyNonEmpty`; mutants | upstream bodies verify; `indexOfPre80390` fails on `result < size`; `lastIndexOfPre80661` fails on `(result == -1) == !contains(element)`; vacuity clean on the port | 300–500 |
| S9a | Constructor models of bound dependency classes | the S9a tests of `docs/targets/arraydeque-ctor-binding.md` | 300–500 |
| S9 | `external-specs/ArrayDeque.kt`, bundled-vacuity entry, spec-equality test, consumer in testData and in the Gradle consumer project | the consumer claims and negatives above; `peekEmpty` fails its precondition; `check-gradle-consumer.sh` passes with the trust report entry | 500–800 |
| S10 | Upstream patch artifact for defect 1 | `docs/upstream/arraydeque-removeall/` (patch, report, reproduction, test driver) plus `ArrayDequeTest` cases using `testArrayDeque`'s layout enumeration with a throwing `contains` at every position; the cases fail on 2.3.0 and pass patched, run against a scratch build and reported in the commit | 200–400 |

Order: S1 → S2; S1 → S4; S3 and S5 are independent of everything else; S5a, S5b and S5c are independent of each
other; S5d–S5g are independent of each other; S6 needs S1, S3, S5, S5a–S5g; S7 needs S2, S6; S8 needs S4, S7; S9a is independent of everything else; S9 needs S8 and S9a. S10 is independent of all verifier work. No stage touches two verifier subsystems.

Stop rule: if S6 or S7 cannot verify a member within the default per-declaration timeout with the frame triggers of
`docs/targets/arraydeque-proof-barrier.md`, stop and report the timings before S8. Do not raise the timeout to
pass. That note owns the timing budget of S7–S9 and the restricted minimum. If one of S5a–S5g needs more than twice its size estimate, or its fix needs a new
trusted assumption, stop and report before S6.

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

### Decision: F3 is not justified

The target ends at the minimum plus the S10 patch. F3, V6 and V8 are not planned. The measurements are in
`docs/targets/arraydeque-results.md`; the patch and its tests are in `docs/upstream/arraydeque-removeall/`.

| Criterion | Status | Evidence |
|---|---|---|
| 1. Headroom | Established for the retained port in the fresh cold run | See [cold per-declaration measurements](arraydeque-results.md#cold-per-declaration-measurements) for the complete attempt inventory and margins. |
| 2. Churn | Not measured | No cheap measurement exists. Outside a `try`, a call has no exceptional edge (`docs/soundness.md`, "Unreachable points"), so a churn branch must first add an exceptional edge to every call in a borrowing member and then assert the class laws there. That is the V6 implementation itself, estimated in the note at an opus design and 2–4 sol changes, and the full suite then needs a `--verify` run. Criteria 3 and 4 fail regardless of the result. |
| 3. Second customer | Fails | ArrayDeque is the only selected target. Persistent collections is not selected, and the Ktor ranges deviations need no exceptional-exit reasoning. |
| 4. Marginal value over S10 | Fails | The S10 tests run upstream's own `ArrayDequeTest` layout enumeration (capacities 2, 8 and 15, every head, tail and size, every throw position) against the shipped source at v2.3.0 and at master, and reject the unpatched build. No reviewer has asked for a capacity-independent claim. A proof would cover a patched port of a fix that is not submitted, under the port's residual trust (D0 and the D-labels); it would not prove the shipped class or the shipped patch. |
| 5. Feasibility | Not run | V8 does not exist, so the P6(b) spike has nothing to run on. |

Costs that weigh against F3 as well:

- Solver time: P6 adds an exceptional-exit obligation at every call inside the three scan loops of
  `filterInPlace`, each with a subsequence witness over a wrapping buffer. The port file without these already
  takes longer than one default declaration timeout in total.
- Soundness: V6 adds obligations and no assumption. P6 for `filterInPlace` additionally rests on the callback not
  reaching the deque, which is the registered "Unique borrows" entry. The soundness cost is low; the cost is churn
  and solver time.

What would reopen the decision: a selected second target that needs V6, or a request for a capacity-independent
claim about the fix. Either one reopens criteria 1, 2 and 5, which then need per-declaration timings, a churn branch
and the V8 spike.

The minimum target is not met yet: items 1, 6 and 8 are open (`docs/targets/arraydeque-results.md`, "Acceptance
audit"). That work is independent of exceptional exits.

## What stays trusted

At the end of the minimum target:

For port correspondence, see ‘D2, D-6 and residual trust: port correspondence’; for array and cast assumptions, see V1 and V2; for equality assumptions, see D4.
- The bundled ArrayDeque model, as for any external spec, with contract correspondence checked by
  `ArrayDequeSpecEqualityTest` under the projection in ‘Bundled model shape’.
- Nothing on exceptional exits.
