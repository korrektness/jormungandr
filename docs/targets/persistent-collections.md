# Target evaluation: kotlinx.collections.immutable

This note evaluates SnaKt work on
[Kotlin/kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable).
It competes with the Ktor ranges and Kotlin `ArrayDeque` notes for selection as a SnaKt target.
It is research and design only. Nothing here is implemented.

## Recommendation

Do not select this library as the next SnaKt target. The library itself deserves one small
action that needs no verifier: report the unmerged regression described in
[Findings beyond the reports](#findings-beyond-the-reports) to the maintainer before it merges.
The report is drafted, unfiled, in [Draft upstream report](#draft-upstream-report).

Reasons:

1. The reported bugs are reproduced, and the maintainer has already fixed all three on an
   unmerged branch. SnaKt would only re-find them.
2. The bugs live in code SnaKt cannot convert today: heterogeneous `Array<Any?>` buffers,
   `Int` bit operations, unchecked casts to type parameters, and builder ownership decided at
   run time by an identity token. Closing any one of these gaps is a research project.
3. SnaKt checks nothing on exceptional exits. Every reported bug is an exceptional-exit bug,
   so even the smallest useful proof needs a new verifier semantics.
4. The bugs need an `equals` or `hashCode` that throws. That is rare in deployed callers.
   No deployed caller found here uses keys whose `equals` can throw.

The library does have one property that makes a verifier useful: two dependent writes with
user callbacks between them. Testing by hand fixed one interleaving and broke its mirror (see
[Findings beyond the reports](#findings-beyond-the-reports)). If exceptional-exit obligations
are ever built into SnaKt for another reason, the ordered builders are a good second
customer. [The staged plan](#staged-plan) is written for that case.

## Pinned upstream

| Ref | Commit | Role |
|:----|:-------|:-----|
| `v0.5.2` | `9f961e4dd419dff58ffea164dd41ebba68acc8d6` | Latest release; same as `master` on 2026-10-21 |
| `family-E` | `0576161c9dc670d90efa680dc6cae2b624d84dc9` | Maintainer's unmerged exception-safety fixes, 45 commits ahead of `master` |
| `trial-merge` | `a6a18122d473be39538ec841caa80003837856fd` | Maintainer's unmerged merge of other family branches |
| published jar | `kotlinx-collections-immutable-jvm-0.5.2.jar`, sha256 `6d7f84d5…0286463` | Used for the reproductions |
| `0.3.7` | `gradle/libs.versions.toml` in this repository | What SnaKt itself compiles against |

Every finding below names the ref it was checked on. Upstream branches are named for the
families of bugs the maintainer is fixing (`family-A` … `family-G`, `trial-merge`). They
change daily.

## Reported versus reproduced

All three issues were filed by a JetBrains Kotlin Libraries developer who maintains the
library. All are open and labelled `bug`.

| Issue | Claim | v0.5.2 | 0.3.7 | `trial-merge` | `family-E` |
|:------|:------|:-------|:------|:--------------|:-----------|
| [#373](https://github.com/Kotlin/kotlinx.collections.immutable/issues/373) | `putAll` of a persistent hash map, or of its builder, into a hash map builder: size 4, 6 keys iterated, after `equals` throws partway | reproduced (4/6, also in `build()`) | reproduced | reproduced | fixed (6/6) |
| [#358](https://github.com/Kotlin/kotlinx.collections.immutable/issues/358) | `removeAll` on a hash set builder: size 2, 1 element iterated | reproduced (2/1) | reproduced | reproduced | fixed (2/2) |
| #358 comment | `addAll` (2/3) and `retainAll` (2/1) do the same | reproduced | reproduced | reproduced | fixed (3/3, 2/2) |
| [#376](https://github.com/Kotlin/kotlinx.collections.immutable/issues/376) | after `next()` throws from `hashCode`, `remove()` on an ordered map builder's key iterator removes the wrong key | reproduced (`[Key(1), Key(3)]`) | reproduced | reproduced | fixed (`[Key(2), Key(3)]`) |

Method: the issue programs were run unchanged against the published 0.5.2 jar and the 0.3.7
jar. For the branches, `core/commonMain` and `core/jvmMain` were compiled with `kotlinc` 2.3.0
and `-Xreturn-value-checker=full`. The one `expect`/`actual` pair was replaced by its JVM body
and `@JsName` was dropped. The v0.5.2 source build gives the same results as the published jar.

The `family-E` fixes are local and cheap. `PersistentHashMapBuilder.putAll` and
`PersistentHashSetBuilder.addAll`/`retainAll`/`removeAll` catch any `Throwable`, recount the
size with `TrieNode.calculateSize()`, and rethrow. The ordered builders do their lookups
before their first write. The ordered iterators advance only after `next()` has found its
element.

## Findings beyond the reports

A fault-injection fuzzer was used to test what the fixes leave open. Keys come from 14 ids
over 9 hash values chosen to collide at levels 0, 1, 2 and 6 of the trie. A global countdown
makes the n-th `equals` or `hashCode` call throw. The fuzzer runs random sequences of `put`,
`remove`, `putAll`, `removeAll`, `retainAll` and iterator `remove` on all four builder kinds.
After each operation it turns faults off and checks:
`size` equals the number of elements iterated, iterated elements are distinct and each is
contained, `build()` equals the iterated contents and has their hash code, and every element
came from the previous contents or the operation's argument.

Over 3000 seeds of 30 steps:

- v0.5.2: both hash builders show size mismatches (the #373/#358/#374 family). Both ordered
  builders show `ConcurrentModificationException` and `NullPointerException` (the
  #375/#376/#377 family).
- `family-E`: the hash builders pass every check. The ordered builders still fail in about
  40% of the seeds. Many failures need only one or two operations.

One root cause is confirmed by hand. It is new on `family-E` and is not reported. The
reproduction and its output on each ref are in [Draft upstream report](#draft-upstream-report).

The cause is the two writes that ordered `put` and `add` make to the inner hash map builder;
the draft explains the window. Because either write can throw after the other has landed, a
fix has to undo the first write, or store both writes without calling user code between them.

Not all fuzzer failures on `family-E` were traced to this root cause. Some
`NullPointerException`s from ordered `remove` and `retainAll` may have other causes (see
[Evidence gaps](#evidence-gaps)).

## Deployment relevance

- Compose. `org.jetbrains.compose.runtime:runtime-desktop:1.7.3` vendors 114 classes of the
  library under `androidx.compose.runtime.external.kotlinx.collections.immutable`, including
  the hash and ordered builders. An upstream fix reaches Compose only when Compose vendors it
  again. Which keys Compose stores in these maps was not checked.
- Kotlin compiler. A local Kotlin source tree, `~/dev/kotlin`, imports the library in 203
  files, mostly `PersistentList`, `persistentListOf`, `PersistentMap` and `PersistentSet`.
  This tree is not a git checkout, so its revision is unknown.
- SnaKt itself depends on 0.3.7 in the `uniqueness` and `locality` modules
  (`uniqueness/plugin/PathTrie.kt`, `MoveSites.kt`, `UniquenessState.kt::mutate`). Its keys
  are SnaKt's own classes, whose `equals` does not throw.

Who triggers the bugs: code whose `equals` or `hashCode` throws, and which catches the
exception and keeps using the builder. Examples are lazy-loading proxies, `equals` that
recurses deep enough to overflow the stack, and keys with a bug. The damage lasts: `build()`
returns an immutable map that is wrong for its whole life, and structural sharing carries the
error into every map derived from it. That makes the severity per occurrence high. The
frequency is low, and no deployed caller found here can trigger the bugs.

## What SnaKt can express today

Each limit below has its rule in SPECIFICATIONS.md or docs/soundness.md, cited by section.

| Library construct | SnaKt today | Source |
|:------------------|:------------|:-------|
| `TrieNode.buffer: Array<Any?>`, mixing keys, values and child nodes | Element access is an opaque library call with no contract that may throw. Only `IntArray` has a model | soundness.md "Arrays" |
| `dataMap and positionMask`, `shr`, `shl`, `countOneBits` | Converted as contract-less calls whose result is only an `Int`. A probe failed to verify `(x and 31) <= 31`, `((x shr 5) and 31) <= 31`, `(x and 3).countOneBits() <= 2` and `1 shl i > 0` for `0 <= i < 31` | probe run on this branch |
| `buffer[i] as K` | Unchecked casts to a type parameter are known to be unsound | soundness.md "Type invariants" |
| `ownedBy === builder.ownership`: a node is mutable only when the builder's token owns it | No counterpart. Ownership is static (`@Unique`/shared); a shared node cannot be written | SPECIFICATIONS.md "Ownership" |
| Recursion over shared trie nodes | No termination measure over shared objects. An `Int` measure on `shift` toward `MAX_SHIFT` would work | SPECIFICATIONS.md "Termination of pure functions" |
| `PersistentOrderedMapBuilder : AbstractMutableMap` | Source subtypes of types with an external specification are rejected, and `MutableMap` has a bundled one | SPECIFICATIONS.md "External specifications" |
| `try { builder.putAll(…) } catch …` followed by use of the builder | `try` in a function that owns anything is rejected | SPECIFICATIONS.md "Rejected constructs" |
| State after an exception | A call outside `try` has no exceptional exit, so no law or invariant is checked when an exception leaves a member | soundness.md "Unreachable points" |
| `equals` of an unbounded `K` | Arbitrary result, may throw. This matches the threat model | SPECIFICATIONS.md "Equality" |
| Bundled `MutableMap.put`/`remove` | Gives size and the key's own value. Says nothing about other keys and nothing on exceptional exit | `plugin/resources/external-specs/AssociativeCollections.kt::MutableMapModel` |

`equals` that may throw is the only part of the threat model SnaKt supports today.

## Candidate deliverables

### A. Small, immediately useful, no verifier change

1. Report the `family-E` ordered-builder regression upstream before `family-E` merges, with
   the reproduction above. It names the two-write window in `put`/`add` that neither order
   closes.
2. Offer the fault-injection fuzzer as a test. Upstream is moving its tests to tables of
   operations (#371 and the `lean-tests` and `test-infrastructure` branches), so the
   contribution would be a generator of fault positions for those tables, not a framework of
   its own.

Cost: one or two days. Value: a defect that would otherwise ship in the next release. This
part competes on real-world value, but it is not SnaKt work.

### B. Ambitious: exception safety of the ordered builders, proved

Prove that the ordered builders keep their link invariant on every exit, normal or
exceptional, of `put`, `remove`, `putAll` and iterator `remove`. The proof treats the inner
hash map builder as an abstract map whose operations may throw. This covers #375, #376,
#377 and the `family-E` regression. It needs the verifier changes listed below, and it
verifies a transcription of the builders, not the upstream file.

### C. Out of reach: the hash trie and structural sharing

A proof of `TrieNode` size bookkeeping under exceptions (#373, #358, #374), or of
persistence (a built map is never mutated by a later builder), needs everything in B plus an
`Array<Any?>` model, encoded bit operations, sound element casts, and token-based ownership
of shared nodes. Each of these is a research project. Estimate: several months, with high
risk that the token-ownership design fails. It is not planned below.

### Comparison

| | A | B | C |
|:--|:--|:--|:--|
| Verifier changes | none | 4 (below) | B plus 4 research items |
| Bugs covered | regression found; fixes unchanged | ordered family (4 bugs) | hash family (3 bugs) and persistence |
| Proof target | none | transcription | transcription |
| Time | days | 6–10 sol changes and 3 opus decisions | months |
| Upstream value | direct | indirect: a proof of a transcription, and the bugs it would have caught | indirect |
| Main risk | maintainer is not interested | the proof rests on the inner map's strong exception guarantee, which #374 shows false in v0.5.2 | design failure |

## Exact proof properties (deliverable B)

Let `H` be the inner map from keys to `LinkedValue(value, previous, next)`, `first` and
`last` the end keys, and `EOC` the `EndOfChain` sentinel.

`Links(B)`, the law of the ordered map builder:

1. `H.size == 0` exactly when `first === EOC`, and exactly when `last === EOC`.
2. If `H.size > 0`: `first` and `last` are keys of `H`, `H[first].previous === EOC` and
   `H[last].next === EOC`.
3. For every key `k` of `H`: if `H[k].next !== EOC`, then `H[k].next` is a key of `H` and
   `H[H[k].next].previous == k`. The same holds with `previous` and `next` swapped.
4. Coverage: following `next` from `first` visits exactly `H.size` keys. This needs a ghost
   position per key, or a recursive `@Pure` walk with an `Int` fuel measure bounded by
   `H.size`.

Clauses 1–3 suffice to fail the proof of the `family-E` regression (clause 3 at the new key)
and of the v0.5.2 case in #375 (clause 3 at the last key). Clause 4 makes iteration over a
builder that satisfies `Links` return every key once. In #376 and #377 the links stay valid
and the iterator's position is wrong, so those proofs need an iterator invariant as well.

`Iter(it)`, the law of an ordered builder iterator, where `n` counts successful `next()` calls
minus successful `remove()` calls, and the ghost flag `removable` says whether key `n` may
still be removed:

1. The keys returned by successful `next()` calls and not removed are the first `n` keys of
   the `next` chain from `first`, in order.
2. The iterator's next key is key `n + 1` of that chain, or `EOC` when `n == H.size`.
3. A successful `next()` sets `removable`. A successful `remove()` clears it. `hasNext()` and
   a `next()` that throws leave it unchanged.
4. If `removable` holds, `remove()` removes key `n`; otherwise it throws
   `IllegalStateException`.

Obligations:

- O1, normal exit: each public mutator meets the `MutableMap` contract (`put`, `remove`,
  `size`) and preserves `Links`.
- O2, exceptional exit: when any call to `H`, or to `equals`/`hashCode` of a key, throws,
  `Links` holds on the builder at the throw, and `H`'s contents are a state between the old
  and the intended new contents. This is the basic exception guarantee. It is weaker than "no
  change".
- O3, iterators: `Iter` holds after every iterator call, including one that throws. In
  particular, after `next()` throws, the following `remove()` removes the element that the
  last successful `next()` returned if that element has not been removed yet, and throws
  `IllegalStateException` otherwise.
  After `next()` throws, the rest of the iteration returns every element that has not been
  returned yet.

## Trust boundaries (deliverable B)

- The inner `PersistentHashMapBuilder` is trusted to give the strong guarantee on single-key
  `put`, `get` and `remove`: if it throws, it is unchanged. #374 shows this is false in v0.5.2.
  The fuzzer finds no violation on `family-E`. Proving the ordered layer on v0.5.2 would
  therefore rest on a false precondition. The proof is meaningful only against `family-E` or
  later, and the fuzzer has to check the assumption on every pinned version.
- The transcription is trusted to match the upstream file. A drift check (below) narrows
  this risk but does not remove it.
- The bundled `MutableMap` model, extended with frame and exceptional clauses, is trusted
  (soundness.md "External specifications").
- `EndOfChain` is assumed different from every user key. Upstream makes it a private
  `object`, so this holds for well-typed callers.

## Required verifier changes (deliverable B)

1. Exceptional-exit law obligation. A member that owns its receiver (`@Unique @Borrowed`)
   proves the receiver's class laws at every exceptional exit: before each call's exception
   edge, and before `throw`. Values stay havocked, as they are after a borrow.
   Opus decision D1: opt-in (`@ExceptionSafe` on the class or member) or always on. Always on
   changes every verified class with laws and every golden. Recommend opt-in.
2. `try` with owned state. With change 1 in place, a `catch` may receive owned objects whose
   predicate and laws are restored and whose values are havocked. This lifts the matching
   item in SPECIFICATIONS.md "Rejected constructs" for receivers of exception-safe members only.
   Opus decision D2: what a `catch` knows about an owned local that the `try` body moved.
   Recommend: it is moved on every path that could have moved it.
3. Exceptional clauses in external specifications. A spec construct for "on throw, the
   receiver is unchanged" (strong) and "on throw, the laws hold" (basic). Add frame clauses to
   `MutableMapModel.put`/`remove` (other keys keep their values) in
   `external-specs/AssociativeCollections.kt`. The contradiction check of
   docs/soundness.md "External specifications" must also cover the new clauses.
   Opus decision D3: syntax and placement (`onThrow { … }` beside `postconditions`, or an
   annotation). Recommend a block in the `@SpecOf` holder.
4. Ghost position or fuel-bounded walk for coverage (clause 4). Needs either `@Pure`
   recursion with an `Int` measure over a map lookup, which exists, or ghost fields on a
   source class, which do not. Recommend the `@Pure` walk first.

Changes 1–3 are general SnaKt features. They are worth building only if another target needs
exceptional-exit reasoning. The note does not show that either competitor does.

## Staged plan

Every stage adds public-entry regression tests under
`formver.compiler-plugin/testData/diagnostics/verification/`. Each test calls the public
member through an owned receiver and states its expectation in the golden.

| Stage | Size | Content | Public-entry tests |
|:------|:-----|:--------|:-------------------|
| 0 | none (A) | Report the regression; offer the fuzzer as a table generator | the JVM reproductions above |
| 1 | opus | D1, D2, D3 written into SPECIFICATIONS.md and docs/soundness.md before code | — |
| 2 | sol | Exceptional-exit law check for opt-in members (change 1) | a class whose member breaks a law, then calls an impure function: fails at the call; the same with the call moved before the break: verifies |
| 3 | sol | `try` with owned receivers of opt-in members (change 2) | `try { c.bump() } catch (e: Exception) {}; verify(lawHolds(c))` verifies; `verify(c.n == old)` fails |
| 4 | sol | `onThrow` clauses in external specs and in the contradiction check (change 3) | a contradictory `onThrow` is reported as `EXTERNAL_SPEC_VACUOUS`; a caller relying on the strong guarantee verifies only with it |
| 5 | sol | Frame and `onThrow` clauses for bundled `MutableMap.put`/`remove`/`get` | `m.put(a, 1); verify(m[b] === old(m[b]))` for `a != b` verifies; it fails without the frame clause |
| 6 | sol | Transcription of `PersistentOrderedMapBuilder` without `AbstractMutableMap`, over `MutableMap<K, LinkedValue<V>>`, with law clauses 1–3 | the `family-E` write order fails O2 at the second write; the v0.5.2 order fails O2 at the mirror write; a write order with an undo step verifies |
| 7 | sol | Clause 4 as a fuel-bounded `@Pure` walk; iterator transcription with `Iter` as its law, for O3 | a #376-shaped iterator fails O3; the `family-E` iterator verifies |
| 8 | sol | Drift check: a script that diffs the transcription against the pinned upstream file, ignoring annotations and spec blocks, and exits 1 on any other change | the check fails on a one-line upstream edit |

Stages 2–5 are independent of this library. Stage 6 is the first that depends on it.

## Executable definition of real-life value

The target delivers real-life value when one of these holds, each checked by a command:

1. Upstream merges a fix for a defect that this work reported, or the maintainer confirms the
   defect.
   `gh issue view <n> -R Kotlin/kotlinx.collections.immutable --json state,labels` shows
   `bug` and the report links the reproduction.
2. A `family-E` (or later) release contains an ordered-builder `put`/`add` that the fuzzer
   cannot break. The fuzzer passes over 3000 seeds of 30 steps against that release's jar.
3. For deliverable B only: SnaKt reports O2 failing on the transcription of a released
   upstream version in a way that a JVM reproduction confirms, before the fuzzer or the
   maintainer finds it.

Criterion 3 is the only one that credits SnaKt. On the evidence here, fuzzing finds these bugs
in seconds, so SnaKt's added value would be a proof of the fixed design, not discovery.

## Upstream contribution strategy

The maintainer files and fixes these bugs alone, in batches by family, on branches named for
the families. Since 2026-08-01 the maintainer has opened 42 of the 44 issues and 29 of the 32
pull requests. Of the two community pull requests, #368 is open and #370 was closed. Interest
in a verifier cannot be assumed.

1. File the `family-E` regression as a new issue, using the text in
   [Draft upstream report](#draft-upstream-report). It follows the maintainer's format: a
   title that states the behaviour, a self-contained program, and the observed output. It
   names the two-write window and proposes no fix.
2. Offer the fault-position generator only if the maintainer responds. Fit it to their tables
   (#371).
3. Mention SnaKt only with a result that is already proved and that names an unreported
   defect. Do not offer proofs of a transcription as upstream tests.

Filing an issue is outward-facing and needs operator approval. The draft below is not filed.

## Draft upstream report

Ready to file as a new issue on Kotlin/kotlinx.collections.immutable. The branch, commit and
outputs were checked on 2026-10-21. Before filing, rerun the program against the current
`family-E` tip, and against `master` if `family-E` has merged.

Title: put on an ordered map builder breaks it when hashCode of the last key throws, on
branch family-E

Body:

> On branch `family-E` at `0576161c9dc670d90efa680dc6cae2b624d84dc9`, `put` of a new key on a
> `persistentMapOf(...).builder()` leaves the builder broken when the second `hashCode` call
> on the stored last key throws. The size counts the new key, and iterating the builder or the
> map it builds throws `ConcurrentModificationException` naming `EndOfChain` as a key. Every
> later `build()` throws too. `add` on a `persistentSetOf(...).builder()` does the same.
>
> Release 0.5.2 (`9f961e4dd419dff58ffea164dd41ebba68acc8d6`) and `trial-merge` at
> `a6a18122d473be39538ec841caa80003837856fd` are not affected by this program: the builder
> keeps size 1 and builds `{Key(1)=a}`.
>
> To reproduce:
>
> ```kotlin
> import kotlinx.collections.immutable.*
>
> class Key(val id: Int) {
>     private var callsUntilThrow = 0
>     fun throwOnHashCodeCall(n: Int) { callsUntilThrow = n }
>     override fun hashCode(): Int {
>         if (callsUntilThrow > 0 && --callsUntilThrow == 0) throw IllegalStateException()
>         return id
>     }
>     override fun equals(other: Any?) = other is Key && other.id == id
>     override fun toString() = "Key($id)"
> }
>
> fun main() {
>     val last = Key(1)
>     val builder = persistentMapOf(last to "a").builder()
>     last.throwOnHashCodeCall(2)
>
>     try {
>         builder[Key(2)] = "b"
>     } catch (e: IllegalStateException) {
>     }
>
>     println(builder.size)     // family-E: 2      0.5.2: 1
>     println(builder.build())  // family-E: ConcurrentModificationException      0.5.2: {Key(1)=a}
> }
> ```
>
> The exception on `family-E`:
>
> ```
> java.util.ConcurrentModificationException: Hash code of a key (kotlinx.collections.immutable.internal.EndOfChain@…) has changed after it was added to the persistent map.
>     at kotlinx.collections.immutable.implementations.persistentOrderedMap.PersistentOrderedMapLinksIterator.next(PersistentOrderedMapContentIterators.kt:27)
> ```
>
> With `persistentSetOf(last).builder()` and `add(Key(2))` in place of the map, `size` is 2 and
> `build()` throws the same exception from `PersistentOrderedSetIterator.next`.
>
> `put` of a new key writes to the inner hash map builder twice: the new key's entry and the
> last key's `next` link. Each write calls `hashCode` of its key. On `family-E` the entry is
> written first, so a throw from the link write leaves the new key in the map but outside the
> chain. In 0.5.2 the link is written first, and a throw from the entry write leaves the last
> key's link pointing at a key that is absent; the last paragraph of #375 reports that case.
> Either order leaves a window in which one write has happened and the other throws.

Not in the body: how the regression was found, and any mention of SnaKt. The method was a
fault-injection fuzzer (see [Findings beyond the reports](#findings-beyond-the-reports)).

## Maintenance costs and risks

- Upstream churn: dozens of branches change daily, and the ordered builders changed in the
  past week. A transcription pinned now is stale within weeks. The drift check makes
  staleness visible but does not keep the transcription current.
- Vendored copies (Compose) lag upstream, so a fix proved upstream does not reach the largest
  deployment directly.
- Exceptional-exit semantics touch every class with laws if they are not opt-in (D1).
- The proof's main assumption (strong guarantee of the inner hash builder) is false in the
  latest release.
- Maintainer interest is unknown, and the maintainer is fixing the same bugs already.

## Comparison with the competing targets

The comparison rests on the code shapes. Ranges are `Int`/`Long` arithmetic and bounds, which
SnaKt already checks (SPECIFICATIONS.md "Integer arithmetic"). That favours Ktor ranges for a
first result. `ArrayDeque` keeps elements in `Array<Any?>` and extends `AbstractMutableList`.
It therefore hits two of the gaps listed here: the array model, and source subtypes of types
with external specifications. Its index arithmetic over `head` and `size` is a better fit than
trie bit operations. The selection should weigh whether a competitor needs exceptional-exit
reasoning. If neither does, changes 1–3 have no other customer, and this target pays their
whole cost.

## Evidence gaps

- Not every ordered-builder failure the fuzzer finds on `family-E` is traced to the two-write
  window. Some `NullPointerException`s from `remove` and `retainAll` on the ordered builders
  may have other causes.
- Only `family-E` and `trial-merge` were built. Other family branches may change the ordered
  builders again.
- The fuzzer covers JVM only and the four builder kinds. It does not cover `PersistentList`
  builders (#345, #347, #357, #362) or the persistent, non-builder operations.
- Compose vendoring was checked in one jar version, 1.7.3. Which upstream version Compose
  vendors, and whether newer Compose releases still vendor it, is not established.
- Which keys Compose stores in the vendored maps, and whether any `equals` there can throw,
  is not checked.
- Who in the field catches exceptions from `equals` and keeps using the builder is inferred,
  not measured. No deployed caller that can trigger these bugs was found.
- Whether the maintainer would accept an external report on an unmerged branch is unknown.
- The Kotlin compiler usage count comes from a local source tree whose revision is unknown.
- The bit-operation probe covered `and`, `shr`, `shl` and `countOneBits` in executable code
  only, not in specifications.
