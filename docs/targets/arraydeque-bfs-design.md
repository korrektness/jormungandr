# Design: the breadth-first-search consumer of the bundled ArrayDeque

Use this note when building or reviewing minimum-target item 6 of `docs/targets/arraydeque-plan.md`: the
compressed-sparse-row breadth-first search that verifies through the bundled `ArrayDequeModel`. It owns the
consumer's claims, its invariants, the verifier change it needs (G13), the scope decision on the vertex bound and the
landing split. The plan owns the minimum target; `docs/targets/arraydeque-ctor-binding.md` owns the constructor
binding; SPECIFICATIONS.md owns the language. Test paths are relative to
`formver.compiler-plugin/testData/diagnostics/`.

## Claims

`bfs(offsets, targets, start, parent, rank): Int` searches the graph whose vertices are `0 until n`, with
`n = offsets.size - 1`, and whose successors of `v` are `targets[j]` for `offsets[v] <= j < offsets[v + 1]`. It
returns `found`, the number of discovered vertices. `rank[v]` is the discovery rank of a discovered vertex and `-1`
otherwise. On normal return:

1. Start: `rank[start] == 0`, and `1 <= found <= n`.
2. Ranks: every `rank[v]` lies in `-1 until found`. A vertex is discovered when `rank[v] >= 0`.
3. Parent edges: every discovered `v` with `rank[v] > 0` has `0 <= parent[v] < n`,
   `0 <= rank[parent[v]] < rank[v]`, and an index `j` in `offsets[parent[v]] until offsets[parent[v] + 1]` with
   `targets[j] == v`.
4. Closure: every successor of a discovered vertex is discovered.
5. Frame: `offsets` and `targets` are unchanged.

Claim 3 is the reachability certificate. Following `parent` from a discovered `v` strictly decreases `rank`, so it
reaches a vertex of rank 0, which is `start`, along graph edges. A cyclic parent map violates the strict decrease at
some link of the cycle, so it cannot satisfy claim 3. Claims 1 and 4 give completeness: every vertex on a path from
`start` is discovered, by induction on the path. Both inductions are the meaning of the claims, stated here; the
verifier checks only the local clauses. No reachability lemma is assumed.

Nothing is claimed for an exceptional exit, and the claims hold on normal return only.

## Preconditions

- `offsets.size >= 1` and `n < 715827882`, the bound of `addLast`'s precondition in the bundled model.
- `parent.size == n` and `rank.size == n`.
- Valid offsets: `0 <= offsets[i] <= targets.size` for every `i`, and `offsets[i] <= offsets[i + 1]` for `i < n`.
- Bounded targets: `0 <= targets[j] < n` for every `j`.
- `0 <= start < n`.

The four arrays are `@Unique @Borrowed IntArray` parameters. Two unique paths cannot alias (SPECIFICATIONS.md,
"Ownership"), so the arrays are distinct with no disjointness clause.

## Algorithm and invariants

```kotlin
fun bfs(offsets, targets, start, parent, rank): Int {
    for i in 0 until n: rank[i] = -1
    rank[start] = 0
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    queue.addLast(start)
    var processed = 0
    var found = 1
    while (!queue.isEmpty()) {
        val u = queue.removeFirst()
        found = scanSuccessors(offsets, targets, parent, rank, queue, start, u, processed, found)
        processed++
    }
    return found
}

fun scanSuccessors(...): Int {
    var f = found
    var k = offsets[u]
    while (k < offsets[u + 1]) {
        val w = targets[k]
        if (rank[w] < 0) {
            check(f < rank.size)
            rank[w] = f
            parent[w] = u
            queue.addLast(w)
            f++
        }
        k++
    }
    return f
}
```

The outer loop's invariant, over `processed` and `found`:

- Partition: discovered is `rank[v] >= 0`, processed is `0 <= rank[v] < processed`, and the frontier is
  `processed <= rank[v] < found`. `0 <= processed <= found <= n` and `found >= 1`.
- Queue: `queue.size == found - processed`, and `rank[queue[q]] == processed + q` for every `q < queue.size`. The
  queue holds exactly the frontier in rank order. Queue uniqueness follows: two positions have different ranks.
- Claims 1–3 and 5 for the current `found`.
- Processed closure: every successor of a processed vertex is discovered.

`removeFirst` returns the vertex of rank `processed`. When the queue is empty, `found == processed`, so every
discovered vertex is processed, and processed closure is claim 4. The closure comes from the completed successor scans
and the empty frontier only.

`scanSuccessors` carries the outer invariant with `processed + 1` in place of `processed` in the queue clauses, and
a partial closure for `u`: every `targets[j]` with `offsets[u] <= j < k` is discovered. Its postcondition states the
outer invariant for the new `found`, including closure for every vertex of rank at most `rank[u]`. That clause is
the closure postcondition the negative sibling of "Tests" fails.

The scan is a separate function. The skipped-successor mutant then fails a closure postcondition
and nothing else: the mutant keeps every loop invariant, since its scan still covers a prefix, and only the clause
that needs the whole range fails. Splitting the loop also keeps each proof modular.

## Scope decision: the vertex bound

`addLast` requires `queue.size < 715827882`. The queue holds distinct vertices of `0 until n`, but distinctness and
range do not bound its size for the solver: that step is the pigeonhole principle, which SMT quantifier
instantiation does not derive. The same step bounds `found` before `f++`. Two designs establish it.

| | (A) Proved bound | (B) Checked bound (recommended) |
|---|---|---|
| Mechanism | `order`/`rank` kept as mutually inverse permutations of `0 until n`; a discovery swaps `w` into rank `found`, so an undiscovered `w` has `found <= rank[w] < n` | `check(f < rank.size)` before each discovery |
| Extra arrays | `order` | none |
| Claim | `bfs` never throws | `bfs` may throw `IllegalStateException` at the check, as far as the proof knows; the claims hold on normal return |
| Real behaviour | identical | identical: there are at most `n` discoveries, so the check never fails |
| Measured | times out in spikes (below) | main loop verifies; scan helper times out at 300 s (below) |

The closure fallback is decided under "Budget and stop rule". (B) does not claim that `bfs` does not throw. The check does
not add an assumption: a failed check is an exceptional exit, and nothing is claimed for it.

Recommendation: build (B). Add (A) only as a stretch landing after (B) lands, replacing the `check` with a `verify`,
if the permutation helper verifies within 150 s. The operator decides; the build stages proceed on (B).

Evidence for (A), from scratch spikes run under a load average of about 25 on 16 cores:

- A four-write swap with both permutation quantifiers in its precondition and postcondition times out (>300 s)
  through SnaKt; the same method over plain Viper sequences verifies in 9 s.
- With G13's encoding (below) emulated on the dumped Viper, the swap verifies in 10.4 s. Adding any one of the frames
  the caller needs (`f < rank.size`, an unchanged-prefix frame, an unchanged-rank frame) times out again. In plain
  Viper each such clause verifies alone in about 10 s, and all of them together time out. Exact pointwise frames
  verify in 7 s in plain Viper and still time out through SnaKt.
- With the emulated encoding, the main loop of (A) verifies in 24–62 s against assumed helper contracts, and a scan
  helper carrying the permutation verified once in 52 s.

## G13: triggers over unique-array reads

### Cause

A quantifier body reads a unique array as `unfolding acc(IntArray_unique(a), write) in a.contents[k]`.
`UnfoldingHoisting.quantified` (`formver.compiler-plugin/core/src/org/jetbrains/kotlin/formver/core/linearization/`)
lifts an `unfolding` above the quantifier and out of its triggers only when the `unfolding` rises; a conjunction in
the body stops it. Three effects follow, all reproduced in scratch spikes:

1. A user trigger `triggers(order[k])` keeps the `unfolding`. Silicon rejects it ("is not a valid Trigger") when the
   body's `unfolding` stays below a conjunction. Where it is accepted, it does not match, and the swap above times out.
2. A quantifier without `old` or `let` gets no derived trigger (`TriggerDerivation.derivedTriggers`), so Silicon
   infers one. For `rank[order[k]] == k` it infers the guard term `isSubtype(typeOf(intToRef(k)), intType())` and the
   nested read `rank.contents[order.contents[k]]`. Each causes a matching loop with the sequence axioms of array
   updates: about 480 000 instantiations in 30 s on the swap's fold query.
3. The guard comes from `LinearizationVisitor.quantifierDomain`, which adds the membership term so that inferred
   triggers can match the boxed variable. It is implied by the `RuntimeType` axiom on `intToRef`.

### Change

- User triggers: linearize each `triggers(...)` argument without `unfolding`s, as `derivedTriggers` already does
  for derived ones (`triggerArg` drops `Exp.Unfolding`). A trigger is a pattern over terms of the body; the field
  read inside the `unfolding` is that term.
- Guard: when a quantifier over an `Int`, `Long`, `Short`, `Byte` or `Char` variable has triggers, user or derived,
  `quantifierDomain` emits the range alone, without the membership term.

Neither part changes what a quantifier states. Triggers change only which instances the solver considers
(SPECIFICATIONS.md, "Triggers"), and the membership term is implied by an axiom, so removing it from a guard is
equivalent. No register entry changes.

Deriving triggers for every quantifier whose body contains an `unfolding` is left out. It would choose the innermost
read (`order.contents[k]`; the current maximal-term rule picks the nested read, which times out) and would change
the triggers of every array quantifier in the suite. The consumer writes its triggers explicitly instead.

### Tests (new file `verification/user_invariants/array_triggers.kt`)

- `permutationSwap`: two `@Unique @Borrowed IntArray`s with the two permutation quantifiers, each with
  `triggers(order[k])` or `triggers(rank[v])`, the four-write swap, and the postcondition
  `rank[w] == f && order[f] == w`. It verifies.
- `permutationSwapWrong`: the same body claiming `order[f] == f`. It fails that postcondition.
- `triggeredBound`: a precondition `forAll<Int> { i -> triggers(a[i]); (0 <= i && i < a.size) implies (0 <= a[i] && a[i] <= b.size) }`
  over a conjunction body, and `verify(a[u + 1] - a[u] >= -b.size)` for an in-range `u`. It verifies.
- `triggeredBoundWrong`: the same with `verify(a[u + 1] - a[u] >= 0)`. It fails.
- The goldens of quantifiers that already carry triggers lose the membership term. Regeneration must report no
  outcome change; an outcome change stops the stage.

G13 changes shared verifier code: the stage runs `./agent-scripts/check-all.sh` and the full `--verify` suite before
it lands. Estimated size: 150–300 lines with tests and goldens.

## Tests of the consumer

Positive file `external_specs/arraydeque_bfs.kt` (`// TRUST_REPORT`), verifying through the bundled model:

- `bfs` and `scanSuccessors` as above, with every quantifier over array reads carrying an explicit trigger on its
  innermost read of the bound variable: `offsets[i]`, `targets[j]`, `rank[v]`, `queue[q]`.
- `enqueueOnce`. `enqueueOnce` and `enqueueOnceFalseGrowth` live in the consumer files.
- `parentCycleExcluded`: given claim 3's clause as a precondition, `verify(!(parent[a] == b && parent[b] == a))` for
  discovered `a`, `b` of positive rank. It verifies.

Negative file `external_specs/arraydeque_bfs_negative.kt`:

- `scanSkipLast`: `scanSuccessors` with the loop bound `offsets[u + 1] - 1` and the same contract and invariants. It
  fails the closure postcondition and nothing else. A timeout does not count as that failure.
- `parentCycleWithoutRanks`: `parentCycleExcluded` without the rank clause. Its `verify` fails.
- `bfsWithoutVertexBound`: `bfs` without `n < 715827882`. It fails the `addLast` precondition in the scan and
  nothing else.
- `enqueueOnceFalseGrowth`.

The trust golden lists `ArrayDeque()`, `addLast`, `removeFirst`, `isEmpty` and `contains` as bundled specs.

## Budget and stop rule

Every declaration of the consumer verifies in under 150 s, the budget of `docs/targets/arraydeque-proof-barrier.md`,
measured in a scratch file per declaration when the file takes over 150 s wall. Spike evidence: with G13 emulated,
the (B) main loop verifies in 23 s, and in 124 s without explicit triggers; the (B) scan
helper times out at 300 s in four variants. The scan helper is the open feasibility risk of (B).

If the scan helper or its mutant does not meet the budget after G13, split the scan body further: a per-edge helper
`visitSuccessor(w)` with a frame contract (old discovered ranks and parents unchanged, a newly discovered `w` with
rank `f` and parent `u`), so that the loop invariant restates only the partial closure and the queue window. If that
also misses the budget, stop and report the timings. The fallback that drops closure (restricted minimum 3 of the
proof-barrier note: parent reachability only) changes the plan's acceptance criterion. It is not taken without an
operator decision. The options then are to accept parent reachability only, to keep closure and raise the
per-declaration budget for this file, or to stop item 6. The recommendation is to keep closure and report the
measured times, because closure is the property that makes the consumer useful.

## Landing split

| Landing | Change | Acceptance | Size |
|---|---|---|---|
| L1 | G13 | the G13 tests; no outcome change in the suite; `check-all.sh` passes | 150–300 |
| L2 | consumer proof fixture (B) | the consumer tests above; vacuity clean for both files; per-declaration times reported | 400–700 |
| L3 | published-plugin consumer | `check-gradle-consumer.sh` compiles `arraydeque_bfs.kt` into the consumer's main source set, with diagnostic markup stripped by `sed -E 's/<!\/?[A-Z_]*!>//g'`, so that the testData and the consumer share one source; `scanSkipLast` goes to the `badClaim` source set and must report an error on its postcondition line; the trust report lists `removeFirst` and `isEmpty` as bundled specs | 100–200 |
| L4 (stretch) | (A) | `check` replaced by `verify` with the `order` permutation, within the budget | 300–500 |

L1 lands alone, before L2. L3 needs L2. L4 is optional and needs the operator's go-ahead after L2.
