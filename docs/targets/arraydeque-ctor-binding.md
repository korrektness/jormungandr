# Design: constructor binding for the bundled ArrayDeque model

Use this note when building or reviewing the stages S9a and S9 of `docs/targets/arraydeque-plan.md`. It owns how a
client constructs a `kotlin.collections.ArrayDeque` through a bundled specification, what happens to unbound
ArrayDeque members, and the fallbacks if S9a fails. The plan owns the stages' place in the order and S9's consumer
claims; SPECIFICATIONS.md, "External specifications", owns the binding rules once S9a lands.

## Problem

The S9 consumer constructs its worklist with `ArrayDeque<Int>()`. The external-specification registry
(`formver.common/src/org/jetbrains/kotlin/formver/common/ExternalSpecifications.kt`) binds class models, which are
interfaces and so declare no constructors, and top-level functions and properties. Constructor bindings use the
form below. Once `ArrayDequeModel` binds the class, `rejectUnsupportedStdlibMember` in
`conversion/StdlibExternalMembers.kt` rejects every unbound callable of `kotlin.collections` whose receiver or
result type has a class model.

The stdlib has no ArrayDeque factory function, so an `@ExternalFunction` model has nothing to bind to.

## Decision

Add `@ExternalConstructor("package.Class")`: a top-level model function that binds a constructor of a dependency
class whose class model is bound in the same specification inputs.

```kotlin
// https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-array-deque/-array-deque.html: empty deque.
@ExternalConstructor("kotlin.collections.ArrayDeque")
internal fun <E> arrayDequeModel(): @Unique ArrayDequeModel<E> = TODO()
@SpecOf("arrayDequeModel") internal fun <E> arrayDequeSpec() {
    postconditions<@Unique ArrayDequeModel<E>> { result -> result.size == 0 }
}
```

SPECIFICATIONS.md, "External specifications", owns the binding rules. Binding diagnostics reuse the registry's
rules, with `CONSTRUCTOR_RESULT` for a result type that does not name the bound class model.

Contracts attach exactly as for an `@ExternalFunction` model: from leading specification blocks or a `@SpecOf`
holder. The model is a `FirSimpleFunction`, so `ProgramConverter.embedFormverContract`, `SpecVacuityProbes`
(`FirNamedFunctionSymbol` branch) and the trust classification in `bindingAssumption` take it unchanged.

The other ArrayDeque constructors, `(Int)` and `(Collection)`, stay unbound, so a call of either is
`UNSUPPORTED_FEATURE`. The port proves the restricted `(Int)` constructor under G12 of
`docs/targets/arraydeque-gaps-design.md`; its bundled binding is outside this design's scope.
`(Collection)` is not ported.

### Unbound members stay rejected

An ArrayDeque member that `ArrayDequeModel` does not declare is rejected, not converted as an opaque call. The
rejection is sound and visible in the diagnostics. An opaque-call exception would let a call whose effect on the
receiver is unknown appear in a verified body without a report, and the consumer needs none. `ArrayDequeModel`
declares exactly the members whose contracts the spec-equality test compares with the port.

### Supertype presentations

`ArrayDequeModel` does not extend `MutableListModel`. A use of an ArrayDeque value at a modelled supertype — an
argument to a `List` or `MutableList` parameter, or a receiver of a bound `kotlin.collections` extension such as
`lastIndex` — would hand the object to a presentation whose observers have no link to `ArrayDequeModel`'s. S9a
tests that such a use is rejected. If it is accepted, S9a rejects it: a class whose model does not extend the model
of a bound supertype is not transferred to that supertype.

## Soundness

A constructor model is a function model whose target is a constructor. The call is converted with the model's
signature in place of the constructor's, as `embedAnyFunction` already does for any bound callable. The caller
proves the model's precondition and assumes its postcondition, result ownership and class laws on return. These are the assumption kinds an `@ExternalFunction` model makes, registered under "External
specifications" in `docs/soundness.md`; no register entry is added. That entry gains the S9a tests.

Construction rules for source classes (construction facts, escape analysis, open predicate chains in
`SignatureCreation.toConstructorSignature`) do not apply: the target is a dependency class, and the bound call never
reaches the constructor signature path. The contradiction check runs on the model as on any function model.

For ArrayDeque the one trusted clause is `result.size == 0` with an owned result. The spec-equality test of S9 checks
it against a proved port function (below), so the bundled clause equals a proved contract of the port's `()`
constructor.

## Trust report

A verified caller of `ArrayDeque()` lists `bundled spec: kotlin.collections.ArrayDeque.ArrayDeque(): ArrayDeque<E>`
(`trustName` of the constructor), next to the member bindings it uses. A user-supplied constructor model lists as
`user spec`; a binding of a constructor that SnaKt verified in the dependency lists as `rebinds verified`.

## Stages

| Stage | Change | Acceptance | Size |
|---|---|---|---|
| S9a | `@ExternalConstructor` in `formver.annotations`; registry binding and `CONSTRUCTOR_RESULT`; bound constructors resolve result uniqueness from the model in `SymbolUniquenessResolver`; audit of `modelSymbol` callers (locality, read-only, observers) for a function model bound to a constructor; SPECIFICATIONS.md and `docs/soundness.md` updates | tests below | 300–500 |
| S9 | as in the plan, plus `arrayDequeModel` and its holder in `external-specs/ArrayDeque.kt`, the port witness, and the constructor in the spec-equality test | the plan's S9 acceptance, plus the S9 tests below | 500–800 |

S9a touches only the external-specification binding and lands alone. It changes shared code, so it runs
`check-all.sh` before landing.

S9a tests, with user specs over `formver.compiler-plugin/testData/support/libraries/externalLibrary.kt` (`Counter(Int)`,
`Cell<T>(T)`):

- `Counter(2)` through a constructor model with `result.value() == initial` verifies `value() == 2`; the same
  caller claiming `value() == 3` fails.
- `Cell(13)` through a generic constructor model verifies its observer; a model whose type parameter bound
  differs from the class's is `INVALID_EXTERNAL_SPEC`.
- A model whose result is `CounterModel?`, or the model of another class, is `INVALID_EXTERNAL_SPEC`
  (`CONSTRUCTOR_RESULT`); one with no matching parameter list is `NO_COMPATIBLE_TARGET`; two models of one
  constructor are `DUPLICATE_MEMBER`; a constructor model of a class without a class model is
  `INVALID_EXTERNAL_SPEC`.
- A constructor model whose postcondition is `false` is `EXTERNAL_SPEC_VACUOUS`.
- The trust report of the positive caller lists `user spec: external.library.Counter.Counter(Int): Counter`.
- A model result without `@Unique` gives a shared object: a write through it is dropped and a claim about it after
  the write fails.
- The supertype-presentation test above, with a class model that does not extend a bound supertype model.

S9 tests, added to the plan's S9 acceptance:

- The port file gains `fun <E> newDeque(): @Unique ArrayDeque<E>` with the postcondition `result.size == 0`,
  returning `ArrayDeque()`. It verifies; the spec-equality test compares `arrayDequeSpec` with its postcondition.
  A spec-equality fixture whose constructor clause differs from the witness fails the test.
- In the consumer file, `ArrayDeque<Int>()` followed by `verify(q.size == 0)` verifies; `verify(q.size == 1)`
  fails; `ArrayDeque<Int>(4)` reports `UNSUPPORTED_FEATURE`; a call of an ArrayDeque member outside the model
  reports `UNSUPPORTED_FEATURE`.
- The Gradle consumer's trust report shows the constructor binding as `bundled spec`.

## Fallbacks

If S9a needs more than twice its size estimate, or a new trusted assumption, stop and report; the claim is then
restricted in this order:

1. Borrowed worklist. The consumer takes `queue: @Unique @Borrowed ArrayDeque<Int>` and calls `queue.clear()`
   first, whose contract gives `size == 0`. The bundled model, the spec-equality test and the trust report are as
   in S9 without the constructor. Construction of the deque is outside the claim: a verified caller cannot create
   one, and the S9 report says so. No verifier change.
2. Source-level claim. The consumer uses the port class from `testData/programs/ports/arraydeque/` directly,
   and its claims are labelled as claims about the port, not about the stdlib class. This drops plan target 6 and
   the bundled model, so it needs an orchestrator decision before it is taken.
