# D4: @TrustedElementEquality

Implementation reference: [SPECIFICATIONS.md, Equality](SPECIFICATIONS.md#equality).
Residual trust and regression-test index: [docs/soundness.md, Executed element equality](docs/soundness.md#executed-element-equality).

Validation:
- Touched fast-loop tests: both pass (one test per file).
- `--verify trusted_element_equality`: passes, 23.328 s wall time.
- `--verify verification/soundness/equality.kt`: passes, 25.005 s wall time.
- Runtime smoke run of the alternating-equals fixture: search results 0 and -1.
- Golden regeneration output inspected.
- Full shared-code checks are assigned to the land gate under session instructions.

Generated snapshots are excluded from the size cap with orchestrator approval. Hand-written changes remain below 800 lines. No upstream submission or deployment is requested.
