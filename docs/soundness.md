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
  implementation that can be reached verifies too. Code that SnaKt does not
  verify because of `@NeverVerify` or `@NeverConvert`, on the declaration or a
  class containing it, or on a constructor of the class it constructs, is
  trusted to meet its declared signature: its pre- and postconditions and its
  `@Unique` and `@Borrowed` annotations. Uniqueness and locality checking skips
  it outside its specifications, and still checks its callers against that
  signature (`locality/plugin/OptOuts.kt`,
  `uniqueness_checker/opt_outs.kt`). A program is verified when
  every declaration in it is: a client of a class whose own law fails still
  verifies, because it relies on the constructor's promise
  (`verification/soundness/construction.kt::constructImpossible`).
- **Library contracts.** `conversion/StdLibConverter.kt` gives Kotlin library
  functions contracts by name, and external specifications give library
  declarations contracts, observers and laws (see "External specifications"
  below). They are trusted, and an override of a library member is trusted to
  comply with them.
- **Skipped declarations.** A declaration that SnaKt does not verify reports
  `VERIFICATION_SKIPPED` with a reason. A body converted without that warning is
  verified, including the body of a local function, which is a declaration of
  its own (`verification/soundness/local_functions.kt`). A local function that
  captures a parameter or local of its enclosing function is skipped
  (`verification/classes/law_bound_local_function.kt`).

## The trust report

SnaKt lists the trusted pieces that each verified declaration's program uses:

- `bundled spec` and `user spec`: the external binding of a class or callable
  that the program calls or reads, from a spec that SnaKt bundles or one the
  user supplies (SPECIFICATIONS.md, "External specifications").
- `rebinds verified`: such a binding of a declaration that SnaKt verified in
  the dependency. The spec's contract replaces the one SnaKt proved, so the
  proof does not cover it. SPECIFICATIONS.md, "External specifications", says
  which dependencies SnaKt recognises as verified.
- `@NeverVerify` and `@NeverConvert`: a callee or custom accessor that SnaKt
  does not verify, through its own annotation or a containing class's.
- `skipped`: a callee or custom accessor of the module that SnaKt skipped with
  `VERIFICATION_SKIPPED`. The program uses its contract, which nothing proves.
- `library contract`: a contract that `conversion/StdLibConverter.kt` gives a
  library function by name.

The report also lists the `@WrappingIntArithmetic` semantic opt-in described in
SPECIFICATIONS.md, "Integer arithmetic". Semantic opt-ins are separate from trusted
assumptions and excluded from trust counts. It introduces no trust-register entry.

The report's first line counts the verified declarations, those that rely on a
trusted piece, and the distinct trusted pieces they rely on, in total and by kind.

A call to another declaration of the module that SnaKt does not skip is not
listed: SnaKt verifies that declaration on its own. `verification_targets_selection`
applies to the whole module, so a declaration it leaves out has no verified
caller. A declaration is listed once its verification succeeds,
whether the verifier ran or the verification cache held the result. README.md,
under `trust_report`, says where the report goes
(`verification/trust_report.kt`).

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

External specifications have a check of their own, which runs in every
compilation (SPECIFICATIONS.md, "External specifications"). The bundled ones
are checked by `external_specs/bundled_spec_vacuity.kt`.

The check finds an inconsistent context only. A fact that is consistent but false
at runtime needs a test that must fail; the register below lists them.

A method whose end no path reaches fails its exit probe. The check reports these
by design:

- `verification/soundness/construction.kt::constructImpossible`: the client of a
  class whose law cannot hold (see "Reading a result").
- `verification/havoc.kt::havocNothing`: no value has type `Nothing`.

## Register

### Termination of `@Pure` functions

A `@Pure` function becomes a Viper function, whose definition is an axiom. The
axiom is sound only if a total function satisfies it, so a recursive `@Pure`
function, direct or mutual, needs a termination measure, which Viper checks.
Without one it is rejected. SPECIFICATIONS.md, "Pure functions and
specifications", has the rules.

The measures are ordered by axioms in `domains/TerminationDomains.kt`: an `Int`
decreases to a smaller `Int` and is bounded below by 0, `false` is below `true`,
and an object is below the objects whose predicates contain its predicate. These
are the orders of Viper's `decreases` library. Once a program has a measure,
every function without one is declared terminating, which holds because a
recursive `@Pure` function without a measure is rejected.

Test: `verification/pure_functions/termination.kt::growing,loopyFalse`.

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

A `catch` sees the state at each site that can throw in the `try` body, as
SPECIFICATIONS.md, "Exceptions", lists them, including an `==` that calls an
`equals` other than value or identity equality. The site's exceptional exit
jumps to each enclosing `catch` before the site's effects apply; a call's
`catch` sees the state before the call. That is sound because a `try` body
holds no owned state (SPECIFICATIONS.md, "Rejected constructs") and shared
state is read as an arbitrary value. A failing arithmetic operation, and an
`IntArray`, `Array<T>` or String index out of bounds, is a proof obligation instead, so a
`catch` never needs to see it. An unmatched
exhaustive `when` has no edge: see "Closed hierarchies".

Tests: `verification/exception_sites.kt::midTry,castCatch,sharedGetCatch,charArrayCatch`,
`verification/soundness/implicit_throws.kt`,
`verification/soundness/equality.kt::equalsCatch`.

### Arrays

An `IntArray`'s predicate holds a sequence of bounded integers. An `Array<T>`'s
predicate holds a sequence of references with no element-type invariant. Both
predicates tie the sequence length to a heap-independent length function, since
an array's length never changes. Generic reads rely on the static-typing
assumption in "Type invariants". Disjoint copies splice the source sequence into
the destination sequence. Filling splices a sequence with the specified length
and repeated reference. These encodings trust the documented stdlib bulk-operation
contracts; same-array copy semantics have no encoding.

Access, bounds and factory behaviour are specified in `SPECIFICATIONS.md`,
"IntArray", "Array<T>" and "Exceptions". The encodings live in
`embeddings/types/IntArrayEmbedding.kt`, `embeddings/types/GenericArrayEmbedding.kt`
and `linearization/LinearizationVisitor.kt`.

Tests: `stdlib/array/int_array_elements.kt::readPastEnd,writeShared`,
`stdlib/array/generic_array_elements.kt::writeShared,readPastEnd,newArrayNonNull`,
`stdlib/array/generic_array_bulk.kt::copyGrow,fillWindow,copyPastEnd,selfCopy`,
`uniqueness_checker/generic_array.kt::fillMovesValue,copyResultMovesDestination`,
`verification/soundness/erased_generics.kt::castArrayElement`,
`verification/exception_sites.kt::sharedGetCatch,charArrayCatch`.

### `StringBuilder`

A `StringBuilder`'s predicate holds its contents as a String: at most
`Int.MAX_VALUE` characters, each a `Char`. A new builder is empty. On a shared
builder, `length` and `toString` give arbitrary values and an append is dropped
(`embeddings/types/StringBuilderEmbedding.kt`).

Tests: `stdlib/string_builder/string_builder.kt::wrongLength,appendSharedForgets`,
`verification/string_domain.kt::overlongBuilderAppend`.

### Value ranges

`domains/IntegerBounds.kt` and the injections of `domains/Injection.kt` give
each `Int`, `Long`, `Short`, `Byte` and `Char` its JVM range, and each String at
most `Int.MAX_VALUE` characters, each a `Char`. A quantified variable of these
types ranges over the same values. The injection into `Ref` is one-to-one, so
equal values are equal references; "Boxed identity" says why `===` cannot
observe that.

Tests: `verification/checked_integer.kt::addOverflow,constantOverflow`,
`verification/string_domain.kt::overlongPureResult`.

### String literals

A String literal is a bodiless function (`domains/StringLiteralFunction.kt`)
whose postconditions give the literal's length and its character at each index.
They are computed from the literal's value.

Test: `stdlib/string/long_literals.kt::wrongLengthOfLongLiteral`.

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
(`embeddings/expression/TypeOp.kt`). The same holds for the parameters at the
entry of a function or a custom accessor (`ProgramConverter.registerAccessor`).
Kotlin typing justifies
them, except for a type argument: the encoding assumes that a value has its
substituted type argument, but the JVM checks it only where Kotlin inserts a
cast. `IterationSequence.get` and the collection templates apply this
substituted element-type assumption to each indexed element of their
iteration snapshot. An unchecked
cast to `Cell<Int>`, `List<Int>` or a type parameter `T` gives a value read as
`Any` a type it does not have, and an assertion about that type can verify with
a false result.

Tests: `verification/soundness/erased_generics.kt` and
`verification/soundness/erased_iteration.kt`, whose `UNSOUND` cases verify,
`verification/classes/interface_family_downcast.kt::downcast`.

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

### Collection builder allocation

`embeddings/expression/CollectionListAllocation.kt` is the shared trusted
allocation boundary for collection builders. It allocates a fresh, unaliased
owned List and binds its iteration observer to the computed sequence. The
List model relates size and indexed elements to that observer; the allocator
adds no other element facts or ownership of referenced elements. `map` and
`filter` construct the sequence by executing their lambdas through the inline
call path.

Test: `verification/inlining/collection_hofs.kt::collectionAllocatorAbuse`
checks consistency, rejects source identity and an unconstrained element value.

### Element equality in collections

The collection specifications state membership with element `equals` and
assume it is reflexive, symmetric and transitive, consistent with `hashCode`,
and unchanged while the element is in a collection. A source `equals` is not
checked against this. SPECIFICATIONS.md, "Equality", has the rules.

Test: `verification/soundness/equality.kt::setAddById,listContainsById`.

### Boxed identity

`===` is rejected where both operands may hold a boxed value of one type. Inside
external specifications, `===` on operands typed by the specification's own type
parameters is allowed: there it states that the collection holds the same
reference. The specifications' storage facts rely on this.

Test: `verification/soundness/equality.kt::boxedInts,boxedGeneric`.

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

A site's exceptional exit ends in `inhale false` after its edges to the
enclosing `catch` blocks (`StmtConversionContext.exceptionalExit`). Outside a
`try`, a call has no exceptional exit: the exception leaves the function, where
no postcondition is claimed. `throw` and a call that returns `Nothing` have no
normal successor, so `inhale false` follows them too.

Tests: `verification/control_flow/throw.kt::throwCaughtSkipsState`,
`verification/negative/try_catch.kt::useException`.

### Property reads

A read of a top-level property, of a property that is not a backing field, or
of a Java static field gives an arbitrary value of its type; a platform type is
nullable. A reference to an `object` gives an arbitrary value of the object's
type at each read. A getter cannot write to state the caller owns, because
locality rejects passing it an owned receiver.

Tests: `verification/soundness/property_reads.kt`,
`verification/java_static_calls.kt::staticFieldIsOpaque`,
`verification/classes/companion_objects.kt::objectPropertyIsOpaque`.

### Iteration sizes

Iteration observers exist only for Collections and Maps and for the sequences
built from them; an `Iterable` that is not a `Collection` is rejected
(`conversion/IterationSequenceConversion.kt`). The List, Set and Map
specifications state that the observed sequence has `size` elements. That
assumes a collection has at most `Int.MAX_VALUE` elements: the JVM's `size`
gives `Int.MAX_VALUE` for a larger one.

Test: `verification/soundness/iterable_size.kt::countVisited,iterableObserver`.

### Closed hierarchies

An exhaustive `when` proves that a branch matches from the enum entries or
sealed inheritors that SnaKt compiles against (`conversion/ExhaustiveWhen.kt`),
and `==` compares by identity on a class whose `equals` is `Any.equals` or
`Enum.equals` for every value (SPECIFICATIONS.md, "Equality"). For a source
class these facts are exact. For a library class they assume that the class at
run time is the version SnaKt compiled against. A newer version can add an
entry or an inheritor, so that the `when` throws, and no `catch` sees that
exception.

Tests: `verification/control_flow/exhaustive_when.kt::nonExhaustiveWhen,objectWhen`
check the source side; no test in one compilation can swap a library version.

### External specifications

An external specification gives a library declaration a contract, purity,
ownership, observers and laws that SnaKt does not prove against the library's
code. A source subtype of a modelled class is rejected. SPECIFICATIONS.md,
"External specifications", has the binding rules and the contradiction check.

The contradiction check assumes the precondition for arbitrary arguments and
refutes `false`, so it finds only a specification that contradicts itself for
every input. A postcondition that contradicts itself for some arguments passes
the check, and a call with those arguments proves anything. The specifications
stay trusted.

The bundled specifications are read from the plugin jar's own index and
extracted to a per-user directory. Each compilation compares the extracted
files with the jar's and restores any that differ
(`formver.common/src/org/jetbrains/kotlin/formver/common/ExternalSpecSources.kt`). The Gradle plugin declares the
specification files as inputs of the compile task, so editing one recompiles
its callers.

Tests: `external_spec_ownership.kt::SourceGauge`,
`external_specs/spec_vacuity.kt`, `external_specs/spec_partial_vacuity.kt`, `external_specs/bundled_spec_vacuity.kt`,
`formver.compiler-plugin/test/org/jetbrains/kotlin/formver/common/ExternalSpecSourcesTest.kt`, and
`agent-scripts/check-gradle-consumer.sh`, which edits a specification and
rebinds a verified declaration.

### Domain axioms

The runtime-type, multiset, equality-relation and termination domains, `havoc`,
`arraySize` and the operator functions are axiomatised. They have a consistent
model.

Test: the vacuity check (`./agent-scripts/test.sh --vacuity`), which reports a
contradiction they cause in any verified test.
