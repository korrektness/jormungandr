# Soundness

SnaKt proves a Kotlin function correct by verifying a Viper program. The proof
says something about the Kotlin program only where the encoding agrees with
Kotlin's semantics. This page lists each place where the encoding assumes a fact
it does not prove: what it assumes, why that is sound, and the test that tries to
abuse it. A test that abuses an assumption must fail verification or be
rejected.

Source paths are relative to
`formver.compiler-plugin/core/src/org/jetbrains/kotlin/formver/core/`, test
paths to `formver.compiler-plugin/testData/diagnostics/`.

## Reading a result

A verified function is correct relative to the contracts it calls. That makes
three things part of every result:

- **Bodiless contracts.** A call uses the callee's header: its pre- and
  postconditions, laws and observer facts. `FunctionSignature.kt` emits a header
  without a body for a callee in another declaration, for an abstract or
  interface member, and for `@NeverVerify`. The result holds only if every
  implementation that can be reached verifies too. A program is verified when
  every declaration in it is: a client of a class whose own law fails still
  verifies, because it relies on the constructor's promise
  (`verification/soundness/construction.kt::constructImpossible`).
- **Library contracts.** `conversion/StdLibConverter.kt` gives Kotlin library
  functions contracts by name. They are trusted, and an override of a library
  member is trusted to comply with them.
- **Skipped declarations.** A declaration that SnaKt does not verify reports
  `VERIFICATION_SKIPPED` with a reason. A body converted without that warning is
  verified, including the body of a local function, which is a declaration of
  its own (`verification/soundness/local_functions.kt`). A local function that
  captures a parameter or local of its enclosing function is skipped
  (`verification/classes/law_bound_local_function.kt`).

## The vacuity check

An assertion verifies vacuously when the verifier's facts at that point
contradict each other. `refute(false)` fails exactly there.

`./agent-scripts/test.sh --vacuity [pattern]` runs the full pipeline with
`refute false` added at the entry and at the end of every Viper method body. It
lists each probe that fails in a declaration that otherwise verifies, as test
file, declaration, Viper method and probe point. It exits 1 when it lists any.
The probes are not reported as verification errors, so goldens still apply. A
probe at a method's end covers only the paths that reach it: a contradiction
confined to one branch needs a probe inside that branch
(`verification/refute.kt::branchHidden,branchProbed`).

The check finds an inconsistent context only. A fact that is consistent but false
at runtime needs a test that must fail; the register below lists them.

A method whose end no path reaches fails its exit probe. The check reports these
by design:

- `verification/soundness/construction.kt::constructImpossible`: the client of a
  class whose law cannot hold (see "Reading a result").
- `verification/havoc.kt::havocNothing`: no value has type `Nothing`.

## Register

### Recursive `@Pure` functions

A `@Pure` function becomes a Viper function, whose definition is an axiom. The
axiom is sound only if a total function satisfies it, so a recursive `@Pure`
function, direct or mutual, needs a termination measure, which Viper checks.
Without one it is rejected. SPECIFICATIONS.md, "Pure functions and
specifications", has the rules.

Test: `verification/pure_functions/termination.kt`.

### Shared writes to law-bound fields

After a call that borrows an object through a shared path, the caller assumes the
object's class laws again. That is sound because verified code cannot write a
law-bound field through a shared path. SPECIFICATIONS.md, "Class laws", has the
rule.

Test: `verification/classes/shared_write_laws.kt`.

### Calls of function objects

A function object has no contract. A function-type parameter can only be
`@Borrowed`, so the call may write to each owned argument but cannot take it:
the caller keeps each owned argument's predicate and forgets the values under it.
Its class laws are assumed again, as after any shared borrow. A function object
cannot capture state the caller owns, because uniqueness checking rejects that
capture. A function object stored in an object during its construction cannot
capture that object either: such a construction makes its objects shared
(SPECIFICATIONS.md, "Annotations").

Tests: `verification/soundness/callbacks.kt`,
`verification/classes/law_function_property.kt`.

### `Int.compareTo`

`compareTo` returns -1, 0 or 1, as on the JVM.

Test: `verification/compare_to.kt`.

### Exceptions inside `try`

A `catch` sees the state at the entry of the `try` body and at each explicit
`throw`. It does not see the state at a cast, a `require`, a `check` or a call
that throws part-way through the body.

Test: `verification/control_flow/throw.kt::throwCaughtSkipsState` covers explicit
`throw` only.

### Shared array reads

A read of an `IntArray` reached through a shared path checks no bounds and gives a
fresh value. Without a `catch` this is partial correctness: the read throws, and
the function does not return. Inside a `try`, the `catch` can observe it.

Test: `stdlib/array/int_array_elements.kt::readPastEnd,writeNegative` covers
owned paths only.

### Unconverted bodies

Callers use constructor headers. SnaKt verifies the constructors, property
initializers and `init` blocks of every source class against those headers
(SPECIFICATIONS.md, "Verification Control"). A construction it cannot convert or
that breaks the construction discipline is skipped, and its header then states
no field values. Top-level initializers are unconverted.

Property accessor bodies are verified against their callers' header
(SPECIFICATIONS.md, "Verification Control").

Tests: `verification/soundness/accessors.kt`,
`verification/soundness/constructor_bodies.kt`,
`verification/soundness/law_free_constructors.kt`,
`verification/soundness/construction_skips.kt`.

### Family transfers and observer views

`embeddings/types/FamilyTransfer.kt` and `ObserverBinding.kt` hand one object
between its class presentation and an interface presentation, and assume that
the observers of the two agree. Nothing proves that the two footprints are the
same.

Tests: `verification/soundness/footprints.kt`,
`verification/classes/zero_law_roundtrip.kt::permanentZeroLaws`,
`verification/classes/observer_domains.kt::outsideWindow`.

### Type invariants

After a read, a cast, a call result or a generic adaptation, the encoding
inhales the value's type and access invariants
(`embeddings/expression/TypeOp.kt`). Kotlin typing justifies them, except for a
type argument: the encoding assumes that a value has its substituted type
argument, but the JVM checks it only where Kotlin inserts a cast. An unchecked
cast to `Cell<Int>`, `List<Int>` or a type parameter `T` gives a value read as
`Any` a type it does not have, and an assertion about that type can verify with
a false result.

Tests: `verification/soundness/erased_generics.kt`, whose `UNSOUND` cases
verify, `verification/classes/interface_family_downcast.kt::downcast`.

### Havocked shared fields

A read through a shared path gives an arbitrary value of the field's type, with
no ownership and no laws.

Test: `verification/classes/untracked_write.kt::writeThroughSharedParameter,storeThroughSharedParameter`.

### Unique borrows

After a call that borrows an owned value, the callee's verified postcondition
returns the predicate; state the callee could change is havocked.

Tests: `verification/classes/unique_borrowed_shared.kt::passToBorrowed,passOneObjectTwice`,
`verification/classes/interface_borrowed_upcast.kt::lostField,inlineLostField`.

### Allocation

A fresh object has exactly the class it was constructed as.

Test: `verification/soundness/construction.kt::allocationExactType`.

### Construction facts

A constructor fixes each default `val` getter once per receiver, and its header
relates parameters to fields. Write and reach analysis removes any relation that
the construction can invalidate. A construction that lets `this` escape gives
its object shared and states nothing about its fields. In an open class, the
construction may call on `this` only members that are fixed for every subclass,
checked through the bodies of the members it calls; a subclass override could
otherwise run before the subclass's fields are assigned.

Tests: `verification/soundness/construction.kt::overriddenValInit`,
`verification/classes/construction_writes.kt`,
`verification/soundness/law_free_constructors.kt::ExtWrite`,
`verification/soundness/open_dispatch_chain.kt`.

### Lambda creation facts

A lifted lambda keeps the facts about its captures that hold at every creation
site and cannot change afterwards.

Test: `verification/pure_functions/lifted_lambda_creation_facts.kt`.

### Override refinement

An override's contract is checked against legal states of the subclass, built by
inhaling the subclass predicate minus the superclass part.

Tests: `verification/classes/override_contracts_negative.kt`,
`verification/classes/inherited_implementation_obligations.kt::InheritedRestricted,InheritedHonestIdle`.

### Unreachable points

`throw` and a call that returns `Nothing` have no normal successor; the encoding
adds `inhale false` after the edges to the enclosing `catch`.

Tests: `verification/control_flow/throw.kt::throwCaughtSkipsState`,
`negative/try_catch.kt::useException`.

### Property reads

A read of a top-level property, or of a property that is not a backing field,
gives an arbitrary value of its type. A getter cannot write to state the caller
owns, because locality rejects passing it an owned receiver.

Test: `verification/soundness/property_reads.kt`.

### Domain axioms

The runtime-type and multiset domains, `havoc`, `arraySize` and the operator
functions are axiomatised. They have a consistent model.

Test: none; the vacuity check would report a contradiction they caused.
