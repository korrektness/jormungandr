# S9 — bundled ArrayDeque model and consumer

The bundled `ArrayDequeModel` binds the no-argument constructor and the port's in-scope members. `ArrayDequeSpecEqualityTest` checks the public-observer contract projection, constructor result and ownership annotations; mutations of constructor, growth and capacity clauses fail the comparison.

## Scope and trust

The projection and capacity claim restrictions are owned by `docs/targets/arraydeque-plan.md`, ‘Bundled model shape’ and ‘Claim restrictions’. The model's size law states only nonnegativity. Growth bounds are caller preconditions. The binding is an external specification, recorded as bundled trust rather than a proof of the shipped class.

The testData consumer `enqueueOnce` proves conditional insertion leaves the vertex last. Its false-growth sibling fails; insertion without the capacity precondition fails; a borrowed deque's upper size bound cannot be proved. Constructor size zero verifies, size one fails, empty indexing fails, and unbound constructors/members are rejected. The trust golden includes constructor and member bindings.

The published-plugin consumer constructs a real `kotlin.collections.ArrayDeque`, inserts a vertex conditionally and proves its last element. The consumer script checks constructor and insertion entries in the trust report. This is the worklist-operation consumer; the plan's full CSR breadth-first-search consumer is outside the evidence here.

## Validation

- `./agent-scripts/test.sh ArrayDequeSpecEqualityTest`: 2 passed.
- `./agent-scripts/test.sh --verify arraydeque_binding`: passed; wall time 44.748 seconds including Gradle overhead.
- `./agent-scripts/test.sh --vacuity arraydeque_binding`: no contradictory context.
- `./agent-scripts/check-gradle-consumer.sh`: exit 0, including the multiplatform explicit-API warnings-as-errors build.
- Golden regeneration reports the intended missing-growth-precondition and false-upper-bound failures.
- Full suite, lint and review are delegated to the land gate.
