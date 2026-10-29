# S7: growth and end mutations

The restricted ArrayDeque source port verifies with growth, modification tracking, both end insertions, add, and both end removals including their nullable variants. No in-scope declaration has a skipped, unsupported, internal-error, untracked-write or timeout diagnostic. The negative siblings have their intended failures.

## Validation and timing

ArrayDeque.kt is the sole testData source file. Wall times include script and Gradle startup.

| Command | Seconds | Result |
| --- | ---: | --- |
| `./agent-scripts/test.sh --verify testArrayDeque` | 68.597 | pass |
| `./agent-scripts/test.sh --vacuity testArrayDeque` | 64.317 | pass; no contradictory context |
| `./agent-scripts/test.sh testArrayDeque` | unmeasured | pass |
| `./agent-scripts/test.sh --verify PortDeviationTest` | 6.893 | 11/11 pass |

The whole-file --verify time bounds each declaration below the 150-second budget. Default timeouts are unchanged. No shared verifier code changes are included; the land gate owns full-suite checks.

## Contracts and tests

- Growth and end-mutation contracts: see formver.compiler-plugin/testData/programs/ports/arraydeque/ArrayDeque.kt.
- rotateLeft and rotateRight prove the complete rotated sequence through public port contracts. Each has an unchanged-sequence negative. rotateRightUnchanged uses two distinct elements to bound its negative fixture; the positive rotateRight proof covers arbitrary permitted sizes.
- endOperations exercises construction, addFirst, Boolean add, and both nullable removals on nonempty and empty states.
- removeKeepsSlot omits the null write and fails its P2 postcondition. addLastWithoutCapacity reports INTEGER_OVERFLOW on its unbounded old(size) + 1 contract expression; this is a specification arithmetic obligation, not a runtime defect claim. checkedCapacity reports INTEGER_OVERFLOW in the unannotated capacity body. copyElementsPastEnd fails the second copy's source upper bound. firstOnEmpty and getWrapped are negative fixtures.

The labelled ArrayDeque.port.diff passes mechanical correspondence and assignment-site checks and contains no window lemma hunk. D-6 verifies cover the three retained storage assignments: empty construction, ensureCapacity's positive allocation and copyElements' positive replacement. The (Int) constructor stays outside the claim under the G12 fallback; DequeSpec stays observer-only as specified by docs/targets/arraydeque-proof-barrier.md.

## S8 guidance and trust

The frame formulation and trigger policy are owned by docs/targets/arraydeque-proof-barrier.md. This port uses its private-callee and public same-index frames, with shifted frames retaining their unshifted-side triggers. There are no physical-frame bridge verifies or window lemma functions. The measured positive rotation clients cover both directions of chained end mutation, and the final file is vacuity-clean. No restricted-minimum reduction is applied to the in-scope API.

Residual trust is owned by formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/PROVENANCE.md. The proof concerns the restricted source port, not the shipped class or exceptional exits. Same-array copyInto remains outside M; its model is unchanged. No new assumption, global equality equivalence, external-source modification, upstream submission or deployment claim is introduced.
