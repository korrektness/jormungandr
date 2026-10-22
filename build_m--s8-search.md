# S8 — ArrayDeque search and clear

The partial source port verifies the upstream bodies of `indexOf`, `lastIndexOf`, `contains`, `clear` and `nullifyNonEmpty`. Wrapped searches retain both physical-segment scans. `clear` proves size zero and P2 for the vacated window.

## Verification evidence

Default timeouts are unchanged. Measured `./agent-scripts/test.sh --verify <file>` wall times:

| File | Seconds |
| --- | ---: |
| `targets/arraydeque/ArrayDeque.kt` | 112.547 |
| `targets/arraydeque/indexOfPre80390.kt` | 48.172 |
| `targets/arraydeque/lastIndexOfPre80661.kt` | 46.251 |
| `stdlib/array/generic_array_elements.kt` | 11.870 |

Every file is below the 150-second per-declaration budget, including conversion and Gradle overhead.

- Touched fast-loop tests: 4 passed.
- `PortDeviationTest`: 12 passed, no skips. The S5 checker accepts the labelled diff and retained reference-assignment sites. No new port reference assignment to `elementData` is added; the existing D-6 verifies hold.
- Normalized function comparisons use the S5 reusable stripper/differ: each annotated mutant changes only its wrapped guard. The unannotated sibling has the same stripped body as `indexOf`; its public caller verifies `result >= -1 && result < size`, and diagnostic assertions admit only its three equality-dependent clauses.
- `./agent-scripts/test.sh --vacuity ArrayDeque`: `No probe found a contradictory context.`
- No in-scope declaration carries `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE`, `INTERNAL_ERROR` or `UNTRACKED_WRITE`.
- The Array.lastIndex borrowing prerequisite passes all four `check-all.sh` checks. It preserves unique storage after an executed length read; genuine moves still reject later array access.

## Exact mutant diagnostics

`indexOfPre80390` drops `isNotEmpty() &&`. Observed:

```text
/indexOfPre80390.kt: error: Could not establish the postcondition on this return.
Failing clause: `result < size` (51:13)

/indexOfPre80390.kt: error: Could not establish the postcondition on this return.
Failing clause: `result < size` (51:13)
```

`lastIndexOfPre80661` uses `head > tail`. Its failing clause is the absence equivalence `(result == -1) == !contains(element)`. Specifications expand `containsSpec`'s existential relation because executable `contains` calls a loop-bearing method. Observed:

```text
/lastIndexOfPre80661.kt: error: Could not establish the postcondition on return.
Failing clause: `(result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == element...` (51:13)
```

The diagnostic tests require these clauses and reject additional unclassified errors.

## Scope and trust

Exactly the port's two scans carry `@TrustedElementEquality`; `contains` calls `indexOf` without that annotation. The trust golden records their scoped assumptions. See docs/targets/arraydeque-plan.md, ‘D4 and V5: element equality’, for the equality trust policy.

The proof targets the port. Correspondence to the vendored source is checked mechanically through `ArrayDeque.port.diff`; it is not proof of the shipped class. Provenance points to D4's owning trust policy.
