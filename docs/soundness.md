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
- **Opaque calls.** For a call or read reported as `OPAQUE_CALL`
  (SPECIFICATIONS.md, "Verification Control"), the result says nothing about
  what the call computes. Test: `language/reporting/opaque_calls.kt::firstQuote,tableEntry`.
- **Skipped declarations.** A declaration that SnaKt does not verify reports
  `VERIFICATION_SKIPPED` with a reason. A body converted without that warning is
  verified, including the body of a local function, which is a declaration of
  its own (`language/soundness/local_functions.kt`). It knows the values it
  captures only through the facts at its declaration
  (SPECIFICATIONS.md, "Local functions and classes").

## Non-view owned aliases

In read-only code, an uncast local alias outside the
[stable view grammar](../SPECIFICATIONS.md#read-only-view-locals), such as an
alias of a mutable owned field, receives a separate uniqueness root. The
checker can therefore track two roots for one object. Viper permission
accounting and pure-result restrictions still apply, but this is a limitation
of the uniqueness analysis. `verification/pure_functions/pure_casts.kt::plainMutableAlias`
exercises this boundary; the cast-bearing laundering controls in that fixture
must be rejected.

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
- `@TrustedElementEquality`: a function's [executed element equality](#executed-element-equality) assumption.
- `throwsOnlyIf`: the `throwsOnlyIf` condition that an external binding gives a
  library function the program calls. A call may be assumed to throw only
  where it holds (SPECIFICATIONS.md, "Conditions for throwing").
- `library contract`: a contract that `conversion/StdLibConverter.kt` gives a
  library function by name.
- `opaque library call`: a library callable without a model that the program
  calls or reads as the opaque library call (SPECIFICATIONS.md, "Verification
  Control").
- `non-retaining library call`: a standard-library callable without a model
  that borrows its receiver and arguments because SnaKt assumes it keeps no
  reference to them (see "Non-retaining library calls" below).
- `snapshot iterator`: `List.iterator()` bound to the bundled model, whose contract assumes the
  list is not mutated through any reference while the iterator is live (see "Snapshot iterators" below).
- `pure element equality`: a collection query bound to a bundled `@Pure` model
  that compares elements or keys (see "Element equality in pure collection queries" below).
- `assumed terminating`: a library callable, operators included, that a
  termination-checked function or one of its source callees calls; see [Termination of functions and loops](#termination-of-functions-and-loops).

The report also lists the arithmetic semantic opt-ins described in
SPECIFICATIONS.md, "Integer arithmetic". Semantic opt-ins are separate from trusted
assumptions and excluded from trust counts. They introduce no trust-register entry.

The report's first line counts the verified declarations, those that rely on a
trusted piece, and the distinct trusted pieces they rely on, in total and by kind.

A call to another declaration of the module that SnaKt does not skip is not
listed: SnaKt verifies that declaration on its own. `verification_targets_selection`
applies to the whole module, so a declaration it leaves out has no verified
caller. A declaration is listed once its verification succeeds,
whether the verifier ran or the verification cache held the result.
[Trust reports](configuration.md#trust-reports) describes output configuration;
`verification/trust_report.kt` exercises the report.

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

### Termination of functions and loops

A function without `decreases` is proved partially correct only: a call that
never returns satisfies any postcondition. A termination-checked function, and
any loop with a variant, is proved to terminate, with an abnormal exit counting
as termination. SPECIFICATIONS.md, "Termination of functions and loops", has
the rules.

Each program verifies one function, with the others as headers, so Silver's
termination plugin cannot see a cycle through other functions. SnaKt finds the
recursion cycle on the Kotlin call graph instead (`conversion/TerminationGraph.kt`).
At each call of the cycle it calls a body-less method whose precondition states
that the callee's measure at the arguments is below the caller's measure in the
pre-state, with an `Int` decreasing and bounded below by 0. A loop saves its
variant at the start of each iteration and asserts the decrease at each jump
back to its head. These checks need no domain axioms.

The analysis rejects any construct it does not handle, so an unanticipated
construct cannot hide a call. The trusted parts are: library and opaque callees
terminate, each listed in the trust report by the one function that assumes it;
a `for` over a range terminates, which its bound invariants
make true; and a final source callee that is not recursive, has no other loop
and calls only terminating functions terminates. Calls through a function
object, of open members, of generated members, and of the recursion cycle from
inside a lambda, whose body may be verified apart, are rejected rather than
trusted. A library call that receives a value of a type a source class may
implement is rejected, since the library may call back into it. A measured
source callee is relied on to be verified: its own program checks its measure,
and the trust report lists it when it is skipped or `@NeverVerify`.

Test: `language/termination/recursive_functions.kt::stuck,unbounded,pong,ding`,
`language/termination/loops.kt::stalls,skips,missingVariant`,
`language/termination/callees.kt::caller,callsOpen,callsFunctionObject`.

### Pure cast definedness

`domains/CastDefinedness.kt` supplies body-verified identity functions. Their
application preconditions check runtime subtype membership or non-nullness in
pure bodies and specifications; their postconditions prove operand identity.
Pure lowering inhales no cast invariants and acquires no ownership. Statement
casts retain exceptional-exit lowering. The supported targets and footprint
limits are specified in SPECIFICATIONS.md, "Pure functions and specifications".
Generic reads retain the assumption in [Type invariants](#type-invariants).

Tests: `verification/pure_functions/pure_casts.kt`, including a failing pure
callee checked in its caller's verification program.

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
cannot capture state the caller owns: uniqueness checking rejects capturing an
annotated owned root, and a root it inferred owned becomes shared at the
capture (see "Lambda values"). A function object stored in an object during
its construction cannot capture that object either: such a construction makes
its objects shared (SPECIFICATIONS.md, "Annotations").

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
primitive array, `Array<T>` or String index out of bounds, is a proof obligation instead, so a
`catch` never needs to see it. An unmatched
exhaustive `when` has no edge: see "Closed hierarchies".

Tests: `verification/exception_sites.kt::midTry,castCatch,sharedGetCatch,shortArrayCatch`,
`verification/soundness/implicit_throws.kt`,
`verification/soundness/equality.kt::equalsCatch`.

### Conditions for throwing

A function `f` with `throwsOnlyIf` has a Viper function `throws_f` whose
precondition is the method's and whose body is the condition
(`throwsConditionFunction`). Each exceptional exit of `f` asserts
`old(throws_f(parameters))` before its `inhale false`, and every site that may
throw, calls included, gets an exceptional exit (`mayThrow`), so no escaping
path skips the assertion. A catch may still receive any exception, so the
paths into a `catch` are unchanged. The exit after the assertion ends in
`inhale false`, so the ownership state it carries reaches no other path.

A call to a callee with a condition branches, just before the Viper call,
into `inhale throws_g(arguments)`, for a callee `g`, followed by the caller's exceptional
exit. The callee proved its condition in its entry state, which is the state
at that point. Inhaling the function application checks its precondition,
which is the callee's precondition in the same state; the call checks the
same precondition, so a failure of the inhale is not reported
(`ReportedVerifierErrors.add`) and the call reports it. A call that dispatches
to an override assumes the condition of the declaration it names: the
refinement method of each override asserts, after calling it, that the
override's condition in the call's state implies the overridden condition in
the entry state (`OverrideRefinement.kt`).

The assertion uses entry state, while the condition's precondition is the
method's; `old(...)` evaluates both in the entry heap, where the precondition
held. JVM failures such as `StackOverflowError` can leave any method and are
outside the claim, as SPECIFICATIONS.md, "Conditions for throwing", states.
An external model's condition is trusted like the rest of its contract; the
contradiction check of external specifications does not cover it.

Tests: `specifications/throws_only_if.kt::throwTooOften,requireTooStrict,callerTooStrict,callerBreaksPrecondition,callerOfUnspecifiedForbidden,currentStateIsNotEntryState,caughtStillChecked,Loose,Unclaused`,
`specifications/external/throws_only_if_external.kt::digitTooStrict,unclausedLibraryCall`,
`specifications/throws_only_if_varint.kt::readVarintTooEager`.

### Exception-free library calls

`isExceptionFreeLibraryCall` (`conversion/ExceptionFreeLibraryCalls.kt`) gives
no exceptional exit to `to`, to the `Pair` and `Triple` constructors, and to
`toString`, `hashCode` and `equals` on a receiver whose static type is
`String`, `Char`, `Boolean` or a signed or unsigned primitive number type. The
claim rests on the library's implementations of these final classes, which
SnaKt does not check; the check names the receiver's static type, so a call on
`Any` or a type parameter keeps its exit. The bundled model of `Map.getValue`
gives the condition `!containsKey(key)`, trusted like the model's other
clauses: it relies on the map's `get` and `containsKey` not throwing, as the
`@Pure` models of those members already do.

Tests: `specifications/exception_free_library_calls.kt::showsInt,hashesString,constructsPair,hashesAny,showsAny,lookupPresent,lookupMissing`.

The same check covers further library calls on trusted knowledge of their
implementations: `String.plus` of a value of those final types, the
`StringBuilder` constructors without a capacity or `CharSequence`, collection
factories that only allocate (a hashed factory's `vararg` overload only for a
key type among those final types), and `add(element)` on a `MutableList` or
`ArrayList` that a `mutableListOf`, `arrayListOf` or `ArrayList` constructor
call created, directly or through a `val` that cannot be overridden and has no
custom getter. Such a `val` is assumed initialized wherever it is read, as all
field reads are. Concatenation and `StringBuilder.append` past
`Int.MAX_VALUE` code units leave by `resourceExhaustionExit`
(`conversion/StmtConversionContext.kt`): catches may receive the exit, and it
checks no `throwsOnlyIf` condition, since resource exhaustion is outside the
claim. `buildString`, `buildList`, `buildSet` and `buildMap` run their lambda
on an unconstrained shared builder and return an unconstrained value; the
claim that the call itself throws nothing else rests on their library
implementations.

Tests: `specifications/exception_free_allocations.kt::concatenates,concatenatesAny,templatesAny,buildsWithCapacity,buildsStringThrowing,anySet,anyMap,addsToLocal,addsToParameter,addsToVar`.

### Arrays

A primitive array's predicate holds a sequence of integers bounded by its element type. An `Array<T>`'s
predicate holds a sequence of references with no element-type invariant. Both
predicates tie the sequence length to a heap-independent length function, since
an array's length never changes. Generic reads rely on the static-typing
assumption in "Type invariants". Disjoint copies splice the source sequence into
the destination sequence. Filling splices a sequence with the specified length
and repeated reference. These encodings trust the documented stdlib bulk-operation
contracts; same-array copy semantics have no encoding.

Access, bounds and factory behaviour are specified in `SPECIFICATIONS.md`,
"Primitive arrays", "Array<T>" and "Exceptions". The encodings live in
`embeddings/types/PrimitiveArrayEmbedding.kt`, `embeddings/types/GenericArrayEmbedding.kt`
and `linearization/LinearizationVisitor.kt`.

Pure shared-element results follow SPECIFICATIONS.md, "Pure functions and specifications".
Their SSA reads unfold the array predicate around the sequence lookup and assume
no result invariants. Residual trust remains the erased-type boundary in
[Type invariants](#type-invariants); the reader rule introduces no element ownership
or additional typing assumption.

A specification's element read unfolds the array predicate around the sequence
alone, so a `@Pure` call in the index takes the owner's predicate folded and
proves its own preconditions; this assumes nothing.

Tests: `verification/pure_functions/array_element_readers.kt`,
`verification/pure_functions/pure_contract_calls.kt::getPastEnd,copyShifted,growWrongHeap`,
`stdlib/array/int_array_elements.kt::readPastEnd,writeShared`,
`stdlib/array/generic_array_elements.kt::writeShared,readPastEnd,newArrayNonNull`,
`stdlib/array/generic_array_bulk.kt::copyGrow,fillWindow,copyPastEnd,selfCopy`,
`uniqueness_checker/generic_array.kt::fillMovesValue,copyResultMovesDestination`,
`verification/soundness/erased_generics.kt::castArrayElement`,
`verification/exception_sites.kt::sharedGetCatch,shortArrayCatch`.

### `StringBuilder`

A `StringBuilder`'s predicate holds its contents as a String: at most
`Int.MAX_VALUE` characters, each a `Char`. A new builder is empty. On a shared
builder, `length` and `toString` give arbitrary values and an append is dropped
(`embeddings/types/StringBuilderEmbedding.kt`).

Tests: `stdlib/string_builder/string_builder.kt::wrongLength,appendSharedForgets`,
`verification/string_domain.kt::overlongBuilderAppend`.

### Value ranges

`domains/IntegerBounds.kt` and the injections of `domains/Injection.kt` give
each `Int`, `Long`, `Short`, `Byte` and `Char` its JVM range, each `UByte`, `UShort`,
`UInt` and `ULong` its unsigned range, and each String at
most `Int.MAX_VALUE` characters, each a `Char`. A quantified variable of these
types ranges over the same values. The injection into `Ref` is one-to-one, so
equal values are equal references; "Boxed identity" says why `===` cannot
observe that.

`embeddings/expression/IntegerOperators.kt::wrapLong` normalizes a mathematical
result `p` before injection: with `H = 2^63` and `M = 2^64`, it returns
`p - M * floor((p + H) / M)`. Viper division by positive `M` is floor division.
The floor inequality places the result in `[-H, H)`, and its difference from `p`
is a multiple of `M`; these uniquely determine the signed 64-bit result even for
full-range products. The constants are mathematical AST expressions. Injection
therefore asserts bounds on the normalized value, rather than the raw product.
The mode selection rules live in SPECIFICATIONS.md, "Integer arithmetic".

Unsigned values are mathematical integers, not reinterpreted signed bits. Their wrapping arithmetic and
`@WrappingNarrowing` conversions reduce the result into the target range modulo 2^8, 2^16, 2^32 or 2^64
(`IntegerOperators.kt::wrapInto`), which is Kotlin's reinterpretation of the low bits. Division and remainder use
Viper's `\` and `%`, which agree with Kotlin's truncating division on non-negative operands. Unsigned bounds above `Long.MAX_VALUE`
are sums of literals (`IntegerBounds.literal`). The runtime types and injections of unsigned types are declared only
in programs that use one.

Tests: `language/arithmetic/unsigned_arithmetic.kt`, `stdlib/array/unsigned_arrays.kt`,
`verification/checked_integer.kt::addOverflow,constantOverflow`,
`verification/wrapping_arithmetic.kt`, `verification/wrapping_long_arithmetic.kt`
(endpoint products, signed negative products, checked isolation and branch-local
nonvacuity probes), `verification/string_domain.kt::overlongPureResult`.

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
no field values. Top-level initializers are unconverted; a read of such a
property is an opaque call.

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
cast. The substituted parameter and result type conditions of calls on instantiated
generic receivers are part of this assumption. Their member contracts come from
the declaration and are verified against its erased bounds.
`IterationSequence.get` and the collection templates apply this
substituted element-type assumption to each indexed element of their
iteration snapshot. An unchecked
cast to `Cell<Int>`, `List<Int>` or a type parameter `T` gives a value read as
`Any` a type it does not have, and an assertion about that type can verify with
a false result.

Tests: `verification/soundness/erased_generics.kt` and
`verification/soundness/erased_iteration.kt`, whose `UNSOUND` cases verify,
`verification/classes/interface_family_downcast.kt::downcast`,
`verification/classes/generic_member_contracts.kt`.

### Havocked shared fields

A read through a shared path gives an arbitrary value of the field's type, with
no ownership and no laws.

Test: `verification/classes/untracked_write.kt::writeThroughSharedParameter,storeThroughSharedParameter`.

### Shared inferred locals

A local declared without a type that is passed to a parameter that neither
owns nor borrows it, stored in a property or local that neither owns nor
borrows it, or assigned a shared value, becomes shared instead of being
reported. A local declared without a type that receives it is shared as well,
so the two aliases are never both unique (SPECIFICATIONS.md, "Inferred
locals"). The uniqueness checker records it as shared from that point, joining
over control flow, and the encoding reads its `var` fields as havocked from
there. Where a declaration requires a unique value, the checker compares
against the flow-sensitive state of the local and rejects a shared one
(`uniqueness/plugin/SharedInferredLocalChecker.kt` under
`formver.compiler-plugin/uniqueness/src/org/jetbrains/kotlin/formver/`). A
call on it to a library member whose model owns its receiver is the opaque
library call, which assumes no contract.

Tests: `ownership/inference/shared_inferred_locals.kt::facts after sharing are lost,shared on one branch,assigned a shared value,facts are lost after storing in a shared property,facts are lost after storing in a shared local,facts are lost after copying into an inferred local,facts are lost after assigning to an inferred local,modelled member on a shared collection`,
`ownership/inference/shared_inferred_locals_rejected.kt`.

### Values that hold no function

A value of type `Nothing` or `Nothing?`, such as `null` or a `throw`,
satisfies every locality contract on a function type (SPECIFICATIONS.md,
"Annotations"). Such a value is never invoked: `null` must be checked before a
call, and a `throw` produces no value. A join of it with a function value
takes that function's contract, so `if (b) null else f` is checked against
`f`. The flag is `FunctionTypeFact.holdsNoFunction`, set by
`FunctionTypeFact.noFunction`. It is applied in
`type/contract/plugin/FunctionTypeFactJudgment.kt` (satisfaction) and
`type/contract/plugin/FunctionTypeFactUnifier.kt` (join identity), and
`TerminalLocalityContractResolver`
(`locality/contract/plugin/ExpressionLocalityContractResolver.kt` under
`formver.compiler-plugin/locality/src/org/jetbrains/kotlin/formver/`) produces it.
The `type/` paths are under the same base.

Tests: `formver.compiler-plugin/locality/testData/diagnostics/contract/null_value.kt`,
`formver.compiler-plugin/locality/testData/diagnostics/contract/null_value_rejected.kt`.

### Unique borrows

After a call that borrows an owned value, the callee's verified postcondition
returns the predicate; state the callee could change is havocked.

Tests: `verification/classes/unique_borrowed_shared.kt::passToBorrowed,passOneObjectTwice`,
`verification/classes/interface_borrowed_upcast.kt::lostField,inlineLostField`.

### Non-retaining library calls

A final standard-library callable without a model whose result and value
parameters are all primitives, `String`, `Unit` or `Nothing`, that has no
context parameters, no receiver of a function type and some receiver that can
be owned (one that is none of those types, an array or an object declaration)
borrows its receivers and arguments
(`formver.common/src/org/jetbrains/kotlin/formver/common/NonRetainingLibraryCalls.kt`).
A parameter of any other type could store the receiver or an owned argument,
so such members are excluded. SnaKt trusts that such a callable keeps no
reference to its receiver; the trust report lists each callable the program
calls this way as `non-retaining library call`, whether or not the call is
also opaque. After the call, an owned receiver keeps only its class invariants
and laws. A receiver or argument of an interface
type may reach source code through calls the library makes on it, such as
`iterator()` inside `Iterable<T>.sum()`; the assumption covers that code too.

Tests: `specifications/external/stdlib_opaque_members.kt::keepsOnlyInvariants,reliesOnChangedState`.

### Snapshot iterators

The bundled model of `List.iterator()`
(`formver.compiler-plugin/plugin/resources/external-specs/Collections.kt`)
returns an owned iterator whose remaining elements are the list's elements at
the call. The real iterator reads the list as it is at each `next()`. SnaKt
trusts that a `List` is not mutated through any reference while an iterator
over it is live. A caller that owns the list cannot mutate it through a
`List`-typed reference, but a `MutableList` passed to a borrowed `List`
parameter can return such an iterator and be mutated afterwards; the trust report lists the bound callable as
`snapshot iterator`. `iterator()` on a receiver whose static type is
`MutableList` or another mutable collection resolves to a different callable,
which has no model and converts as the opaque library call.

A `for` loop over a `List` that is not owned does not use the model's
contract: the loop's iterator is owned, with unconstrained remaining elements.
Only the loop calls `hasNext()` and `next()` on it, once per step, so the
elements a real iterator returns are always some such sequence; this assumes
nothing about the list.

Tests: `specifications/external/stdlib_member_specs.kt::iterates,iteratesTooFar,iteratesMutableList`,
`language/control_flow/for_collections.kt::mutatedThroughAlias`,
`language/control_flow/for_collections_fail.kt`.

### Element equality in pure collection queries

The bundled models of `Collection.contains`, `List.contains`, `List.indexOf`, `Set.contains`, `Map.containsKey`,
`Map.containsValue` and `Map.get` are `@Pure`, so a call has no exceptional exit and changes no state. The real
functions call the `equals` and `hashCode` of the elements or keys, which a class may override to throw or to have
effects. SnaKt trusts that the element `equals` and `hashCode` these queries use neither throw nor have effects; the
trust report lists each bound query a verified declaration calls as `pure element equality`.

Test: `specifications/external/stdlib_member_specs.kt::mapSet`.

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

### Executed element equality

`@TrustedElementEquality` selects the function-local encoding described in SPECIFICATIONS.md, "Equality".
Residual trust: each executed `equals` on the element type agrees with the specification relation, so its result
is deterministic. The call does not reach the deque; this relies on [Unique borrows](#unique-borrows).

Tests: `stdlib/array/trusted_element_equality.kt` covers search contracts, unannotated siblings, nullable operators,
non-type-parameter operands, catches and inline mode boundaries.
`verification/soundness/equality.kt::alternatingSearchResults` labels the consequence of violating this assumption
with an alternating `equals` as UNSOUND.

### Boxed identity

[Specification identity](../SPECIFICATIONS.md#equality) compares model references;
its scope includes source specifications and laws. Executed comparisons, including
`@Pure` bodies, retain the boxed-identity rejection. External model elements have
the exemption defined there. No additional trusted assumption is introduced.

Tests: `verification/soundness/equality.kt::boxedInts,boxedGeneric`,
`verification/soundness/spec_identity.kt`.

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

### Lambda values

A lambda value is a fresh value of its function type with no other facts, and a
call of it is a call of a function object (see "Calls of function objects"),
so no fact about its body reaches the caller. Its holder may run it at any
later point, so from its creation on each local `var` it captures reads as an
arbitrary value of its type: the conversion keeps a flag per such `var`, set at
creation, and a read under the flag havocs the `var` first. A `var` that no
lambda value captures stays tracked. A captured object is shared: an annotated
owned root cannot be captured, and a root the uniqueness checker inferred owned
becomes shared at the capture: the conversion gives up whatever share of its
unique predicate the function holds there, so its field reads give arbitrary
values and a later use that needs ownership fails. A
lambda that Kotlin marks as called in place has no flags; if the conversion
has to keep one that captures a `var` as a value, it rejects it.

The body of a lambda value written in a function body is a Viper method of its
own with no contract (`valueMethod` in `conversion/LambdaValues.kt`), verified
in its own program. A lambda value written in a property initializer or an
`init` block is not verified, which loses failures in its body but assumes
nothing. The body assumes nothing about what it captures: each captured `val`,
parameter or receiver is one unconstrained value of its type, declared on entry;
each read of a captured `var` is a fresh unconstrained value, and a write to it
is evaluated and dropped. Captured objects are shared in the body. Annotated
owned roots are never captured, as the uniqueness checker rejects that.

Tests: `language/functions/lambda_escaping.kt`,
`ownership/functions/capture_rejected.kt`.

### Override refinement

An override's contract is checked against legal states of the subclass, built by
inhaling the subclass predicate minus the superclass part.

Tests: `verification/classes/override_contracts_negative.kt`,
`verification/classes/inherited_implementation_obligations.kt::InheritedRestricted,InheritedHonestIdle`.

### Unreachable points

A site's exceptional exit ends in `inhale false` after its edges to the
enclosing `catch` blocks (`StmtConversionContext.exceptionalExit`), and after
the `throwsOnlyIf` check described in "Conditions for throwing". Outside a
`try`, in a function without `throwsOnlyIf`, a call has no exceptional exit:
the exception leaves the function, where no postcondition is claimed. `throw` and a call that returns `Nothing` have no
normal successor, so `inhale false` follows them too. Ownership bookkeeping
marks an explicit `throw` exit dead and emits no ownership operations there; a merge takes
its state from the edges that reach it. For the supported abnormal arms of
owned conditional moves, see SPECIFICATIONS.md, "Moves".

Tests: `verification/control_flow/throw.kt::throwCaughtSkipsState`,
`verification/negative/try_catch.kt::useException`,
`verification/classes/conditional_owned_storage.kt`.

### Property reads

A read of a top-level property without a custom getter, of a property that is
not a backing field, or of a Java field gives an arbitrary value of its
type; a platform type is nullable. A write to a Java field is dropped. A read of a top-level property with a custom
getter calls the getter: a `@Pure` getter with a body calls that pure function, and any other custom getter is called and its result is an arbitrary value of its type. A reference to an `object` gives an arbitrary value of the object's
type at each read. A getter cannot write to state the caller owns, because
locality rejects passing it an owned receiver.

Tests: `verification/soundness/property_reads.kt`,
`verification/soundness/accessors.kt::topLevelReadsDiffer`,
`verification/java_static_calls.kt::staticFieldIsOpaque`,
`diagnostics/language/properties/delegated_and_java_fields_fail.kt::javaFieldWriteIsNotKept`,
`verification/classes/companion_objects.kt::objectPropertyIsOpaque`.

### Iteration sizes

Iteration observers exist only for Collections and Maps and for the sequences
built from them; an `Iterable` that is not a `Collection` is rejected
(`conversion/IterationSequenceConversion.kt`). The List, Set and Map
specifications state that the observed sequence has `size` elements. That
assumes a collection has at most `Int.MAX_VALUE` elements: the JVM's `size`
gives `Int.MAX_VALUE` for a larger one.

Test: `verification/soundness/iterable_size.kt::countVisited,iterableObserver`.

### Fresh results of library calls on objects that are not owned

An unchecked library call of an extension whose model declares its result
`@Unique` and consumes no parameter, such as `map`, `filter` or `mapValues`,
gives an owned result with unconstrained contents
(`specifiesFreshExtensionResult` in `conversion/StmtConversionContext.kt`).
This trusts the model's `@Unique` result to mean the library returns an object
that no other reference reaches. An extension that consumes a `@Unique`
parameter, such as `orEmpty`, can return that parameter, so its result on an
object that is not owned stays shared. Member calls are excluded, since a
source implementation of the receiver's type can override them.

Tests: `language/inlining/shared_collection_hofs.kt::sharedMapThenLoop,sharedFilterThenAny,sharedFilterForEach,sharedMapThenCheckedMap,sharedOrEmptyAliasesReceiver`.

### Sizes of collections that are not owned

A read of `size` on a Collection or a Map that is not owned is the unchecked
library call, and its result is at least 0
(`convertUnownedSpecifiedMember` in `conversion/StmtConversionContext.kt`).
The JVM's `size` counts elements and gives `Int.MAX_VALUE` for a larger
collection. A source implementation whose `size` is negative breaks this
assumption.

Tests: `specifications/external/shared_list_members.kt::sharedSizeNonNegative,sharedSizeMayBeZero`.

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

An external specification gives a library declaration a contract, a
`throwsOnlyIf` condition, purity, ownership, observers and laws that SnaKt does
not prove against the library's code. SPECIFICATIONS.md, "External specifications", has the binding rules and
the contradiction check.

A class with a modelled supertype that is not itself bound, in source or in a
library, constructs shared objects, so a call that owns its receiver through
the model is the opaque library call on it. Two parts of a model still apply to
an object that is not owned: the `key` and `value` of `Map.Entry`, read as
values fixed for the object, and the iteration sequence a `for` loop or a
collection template reads from an `Iterable`. A source implementation whose
`key` or iteration changes between reads breaks those assumptions, as a
library implementation does. The constructor rule is in
`resolveConstructorResultUniqueness`
(`uniqueness/plugin/SymbolUniquenessResolver.kt` under
`formver.compiler-plugin/uniqueness/src/org/jetbrains/kotlin/formver/`).

Tests: `specifications/external/source_implementation.kt::model member on the object is the unchecked library call,entry implementation is read as fixed`,
`specifications/external/source_implementation_rejected.kt`. No test runs a
loop over a source implementation.

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
`external_specs/constructor_binding.kt`, `external_specs/constructor_invalid.kt`, `external_specs/constructor_shared.kt`,
`external_specs/spec_vacuity.kt`, `external_specs/spec_partial_vacuity.kt`, `external_specs/bundled_spec_vacuity.kt`,
`formver.compiler-plugin/test/unit/org/jetbrains/kotlin/formver/common/ExternalSpecSourcesTest.kt`, and
`agent-scripts/check-gradle-consumer.sh`, which edits a specification and
rebinds a verified declaration.

### Domain axioms

The runtime-type, multiset, equality-relation and termination domains, `havoc`,
`arraySize` and the operator functions are axiomatised. They have a consistent
model.

Test: the vacuity check (`./agent-scripts/test.sh --vacuity`), which reports a
contradiction they cause in any verified test.
