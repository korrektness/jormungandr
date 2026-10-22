# Design: frame triggers for the ArrayDeque mutators

Use this note when writing or reviewing the frame clauses of the ArrayDeque port, `DequeSpec` and the bundled
model in the stages S7–S9 of `docs/targets/arraydeque-plan.md`. It owns the contract formulation of those frames,
the timing budget of S7–S9 and the restricted minimum. The plan owns the stages and their acceptance;
SPECIFICATIONS.md, "Triggers", owns what a trigger means.

## Decision

A mutator calls `registerModification` and `ensureCapacity` before it writes the buffer. Its frame proof needs
the frames of those callees in the state between the calls and the write. A callee frame
`elementAt(i) === old(elementAt(i))` with its derived trigger fires only where that state's `elementAt` is named,
and no term of the mutator's proof names it. The frames are therefore written with the entry term as a second
trigger. This is a contract formulation: it needs no verifier change, no new assumption and no claim restriction.

- Every frame of a private callee that a mutator calls before it writes (`registerModification`,
  `ensureCapacity`, `copyElements`) is
  `forAll<Int> { i -> triggers(elementAt(i), old(elementAt(i))); (0 <= i && i < size) implies (elementAt(i) === old(elementAt(i))) }`.
- Mutator bodies carry no bridge `verify` and no window lemma functions; the wrapped and unwrapped windows need no
  separate treatment.

With the second trigger, the goal's entry-state `elementAt` instantiates the first callee's frame, which names
`elementAt` in that callee's post-state; that term instantiates the next callee's frame, until `elementAt` names
the state just before the write, whose definition unfolds to the slot the write changes.

## Reduced reproduction

`verification/user_invariants/observer_frame_triggers.kt` (relative to `formver.compiler-plugin/testData/diagnostics/`)
has a circular buffer `Ring` with the port's representation law, `positiveMod` and `elementAt`, a frame-only
member `touch` with the derived trigger, and `touchBoth` with both triggers.

| Function | Shape | Outcome |
|---|---|---|
| `observerAfterTouch` | `touch()`, then `verify(elementAt(i) === old(elementAt(i)))` | verifies |
| `physicalCurrentAfterTouch` | `touch()`, then `verify(elementData[head + i] === elementAt(i))` | verifies |
| `physicalAfterTouch` | `touch()`, then `verify(elementData[head + i] === old(elementAt(i)))` | fails |
| `physicalAfterTouchBoth` | the same after `touchBoth()` | verifies |
| `pushNoCall` | a write at the end, with the frame postcondition | verifies |
| `pushAfterTouch` | `touch()`, then the same write | fails the frame postcondition |
| `pushAfterTouchBoth` | two `touchBoth()` calls, then the same write | verifies |
| `pushFrontAfterTouchBoth` | two `touchBoth()` calls, then a front insertion with the shifted frame | verifies |
| `pushFrontWrongFrame` | the front insertion claiming the unshifted frame | fails the frame postcondition |

The first two rows are the two halves of the bridge that `physicalAfterTouch` asserts. Each half verifies and only
their composition fails, so the failure is not a missing physical-frame contract or an encoding defect of the
observer or the heap snapshot: both halves are provable from the emitted Viper. `./agent-scripts/test.sh --verify
observer_frame_triggers` takes about 31 seconds wall.

A trigger on the asserted `verify(forAll ...)` and quantified lemma functions asserted in the body do not supply
the missing term; SPECIFICATIONS.md, "Triggers", says why.

## Soundness

The formulation changes triggers only. See SPECIFICATIONS.md, "Triggers", for the semantics. The vacuity check of the port's file, which the
plan requires, detects a formulation that hides a contradiction. No register entry changes.

Matching loops: an instance of a same-index frame, from either trigger, yields only the other side's term at the
same index, which is a trigger of the same instance. A chain of instances moves one call state per instance, so
the number of instances per index is bounded by the number of calls on the path.

## Trigger rule for frames

1. A frame whose two sides read `elementAt` at the bound variable itself gets both triggers: the private callees
   above, and every same-index public frame (`addLast`, `add`, `removeLast`, `removeLastOrNull`, `set`, `get`,
   `first`, `last` and their `OrNull` forms). On public frames the rule serves clients that chain calls; the
   members' own proofs do not need it. `rotateRight` needs it on `removeLast`: its goal names `elementAt` only in the
   entry and the final state.
2. A shifted frame (`addFirst`: `elementAt(i + 1) === old(elementAt(i))`; `removeFirst`:
   `elementAt(i) === old(elementAt(i + 1))`) keeps the trigger on its unshifted side, which SnaKt derives or infers.
3. `clear`'s null-slot clause over `elementData[old(internalIndex(i))]` for `0 <= i < old(size)` has the explicit
   trigger `old(internalIndex(i))`.
4. `DequeSpec` and the bundled model carry the port's triggers, since the spec-equality test compares contracts
   as written.

`DequeSpec` declares the observers `size` and `elementAt` only. The spec-equality test compares the model's
mutator contracts with the port's `@SpecOf` holders, so interface mutator declarations would add no checked claim,
and each `override` would be a further port hunk.

## Timing budget

Every in-scope declaration of S7–S9 verifies in under 150 seconds, half the default timeout: the headroom
condition of the plan's "Deciding on exceptional exits". The wall time of `./agent-scripts/test.sh --verify` on a
test file covers Gradle start-up, conversion and every declaration, so it bounds each declaration. A stage whose
file takes over 150 seconds wall measures its slowest declaration in a scratch file before it lands; a declaration
over 150 seconds stops the stage.
| Stage | File | Estimated wall | Budget |
|---|---|---:|---:|
| S7 | `targets/arraydeque/ArrayDeque.kt` | 70–90 s | 150 s |
| S8 | the same file | 90–130 s | 150 s |
| S9 | the consumer file and the bundled spec | 30–60 s each | 150 s each |

## Restricted minimum

If a stage cannot meet its budget or its acceptance without a verifier change or a new assumption, the claim is
restricted, in this order, and the plan's minimum target records the restriction:

1. A public frame whose rule-1 triggers cause a matching loop or a timeout in a client keeps its derived trigger
   only; clients state their goals with the current observer, and `rotateRight` leaves the acceptance.
2. If `lastIndexOf` cannot meet the budget, it and the KT-80661 mutant leave M; `indexOf`, `contains` and the
   KT-80390 mutant stay.
3. If the consumer's closure claim cannot be proved, the consumer claims parent reachability only, and
   `enqueueOnce` stays if `contains` is in M.

The floor is the S6 skeleton, all of S7 (any client that adds needs growth), `indexOf`, `contains`, `clear`, the
KT-80390 mutant, the bundled model with its spec-equality test, and a consumer that verifies through it. The `(Int)`
constructor stays outside the claim under its G12 fallback.
