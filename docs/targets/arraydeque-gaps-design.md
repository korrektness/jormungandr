# Design: verifier gaps blocking the ArrayDeque skeleton

Use this note when implementing or reviewing the four prerequisite stages S5d, S5e, S5f and S5g of
`docs/targets/arraydeque-plan.md`. It owns their designs: the cause, the encoding, the soundness argument, the
change, the tests and the documentation each stage updates. The plan owns the stage order and the acceptance of
S6. Source paths are relative to `formver.compiler-plugin/core/src/org/jetbrains/kotlin/formver/core/`, test paths
to `formver.compiler-plugin/testData/diagnostics/`, as in `docs/soundness.md`.

Each stage adds its reduced reproduction, shown under its "Cause", as a test in its own file.

## G9: conditional initialization of owned storage (S5d)

### Cause

```kotlin
class ConditionalStorage {
    private var data: @Unique Array<Any?>
    constructor(capacity: Int) {
        data = when {
            capacity == 0 -> arrayOfNulls(0)
            capacity > 0 -> arrayOfNulls(capacity)
            else -> throw IllegalArgumentException("capacity: $capacity")
        }
    }
}
```

The constructor reports `HOLE_BELOW` ("the result.data was consumed by `data = when {`") and is skipped. The same
`when` without the `throw` arm verifies, and so does a statement `if` whose `else` throws. Two facts combine:

1. `linearization/LinearizationVisitor.kt`, `yieldsFreshUnique` decides whether a conditional or block yields a
   fresh unique value on every path. It accepts an arm that is a fresh unique value or an `If`/`Block` of them.
   The `throw` arm is `Block { exception; exceptionalExit() }`, which ends in `Unreachable`, so it is rejected. The
   `when` is then an ordinary value, `finishMove` releases the target field (`else -> state.release(this, target)`),
   and the constructor cannot fold its result.
2. `visitUnreachable` emits `inhale false` but leaves the fold state live. Even with the arm accepted, its end state
   would reach the join without the arm's temporary held, and `FoldTrie.join` would drop the temporary.

`LocalThenAssign` (`val d: @Unique Array<Any?> = when { …; else -> throw … }; data = d`) fails the same way.

### Encoding

- `visitUnreachable` kills the fold state after `inhale false`: the state at that point is the dead state, as
  after a jump (`FoldTrie.kill`). A dead snapshot is the identity of `FoldTrie.join` and of
  `FoldTrie.mergeShapes`, so a branch or label merge takes its state from the edges that complete.
- An expression ends abnormally when it is `Unreachable` or a `Goto`, or a `Block` whose last expression ends
  abnormally. `throw`, `return`, `break`, `continue` and a call returning `Nothing` all convert to such an
  expression (`docs/soundness.md`, "Unreachable points").
- `yieldsFreshUnique` accepts an arm of an `If` that ends abnormally, and `movedInto` leaves such an arm
  unchanged: no assignment to the temporary is added after its jump. The rule applies to arms only. A value that
  is itself a `throw` is not a fresh unique value, so it keeps its present lowering; applying the rule at the top
  level adds a temporary to every `throw` expression and changes the goldens of `throw.kt` for nothing.
- The two sites that linearize an exceptional exit into a bare `asBlock` and put it under an `if` with no other
  arm, the checked binary operation in `visitBinaryOperatorExpEmbedding` and `updateStringBuilder`, wrap that
  `asBlock` in `withFoldStateRestored`, as the loop body does. The abnormal block ends dead, so the join gives the
  state before it, and nothing is normalized. Without this the kill inside the abnormal block would kill the state
  of the code that follows it. `branchBlocks` is wrong here: its normalization folds the `StringBuilder` that
  `updateStringBuilder` has just opened, and the following field write then lacks its permission (with
  `branchBlocks`, `string_domain.kt::overlongBuilderAppend` and
  `stdlib_hof_invariants.kt::fillOwnedBuilder,myEachOwnedBuilder` report Viper errors).

The Viper text of a function changes only where a conditional has an arm that ends abnormally and its value is
moved into owned storage.

### Why it is sound

The fold state decides which `fold` and `unfold` statements are emitted and which programs conversion rejects.
It assumes nothing: Viper checks every permission the emitted program uses, and `FoldTrie.acquire` of a fresh
unique value emits no `inhale`; the callee's postcondition supplies the predicate. On a path past `inhale false`
Viper proves everything, so the statements emitted there do not affect what is proved on any feasible path, and
leaving them out loses nothing. The merged state is the state of every edge that reaches the merge, which is what
the join computes for live edges today.

The second half of the change is what could be unsound if it were wrong: a dead state on a live path. With the
state dead, `ownedReceiverPath` finds no owned path, so a write through a unique path would be lowered as a shared
write and dropped. The two `asBlock` sites are the only places where an exceptional exit is linearized outside a
branch merge (`grep -n asBlock linearization/*.kt`); the stage changes both, and a test covers each (below). The
existing `string_domain.kt::overlongBuilderAppend` and `stdlib_hof_invariants.kt::fillOwnedBuilder` already fail
when the builder site is wrong.

Nothing is newly trusted. No register entry changes; "Unreachable points" gains the statement that ownership
bookkeeping treats such a point as unreached.

### Change

About 25 lines: `FoldState.kill` delegating to `FoldTrie.kill`, the kill in `visitUnreachable`, `endsAbnormally`
and `armYieldsFreshUnique` with their uses in `yieldsFreshUnique` and `movedInto`, and the two
`withFoldStateRestored` sites. No conversion-stage change.

### Tests

New file `verification/classes/conditional_owned_storage.kt`, with `--verify` timings reported in the land commit:

- Verifying: `ConditionalStorage` (the three-way `when` with a `throw` arm), followed by
  `verify((capacity == 0) implies (data.size == 0))` and `verify((capacity > 0) implies (data.size == capacity))`;
  `LocalThenAssign`; a two-arm `when` whose second arm calls `error(...)`; a member function of a class with an
  owned receiver (`@Unique @Borrowed`) that reassigns its storage from a `when` with a `throw` arm and then writes
  an element and reads it back.
- Failing siblings: the same constructor with the claim `verify(data.size > 0)` fails (the `capacity == 0` arm);
  a `when` whose throwing arm is replaced by an arm yielding a shared array (`sharedArray()`, not `@Unique`) still
  reports `UNSUPPORTED_OWNERSHIP`, so the rule accepts only arms that end abnormally.
- Live-state guard for the checked-operation site: inside a `try`, a `String` concatenation (which has an
  exceptional exit) followed by a write to an owned receiver's field and a `verify` of the written value. It
  verifies only if the state after the exceptional-exit branch is live; with the kill and a bare `asBlock`, the
  write is dropped and the `verify` fails. The builder site is guarded by the existing tests named above.
- Existing: `verification/control_flow/throw.kt` and `verification/negative/try_catch.kt` keep their outcomes.

The full suite's outcome check (`VerificationOutcomes`) must report no outcome change outside the new file, and
no existing golden outside `targets/arraydeque/` changes.

### Documentation

- `docs/soundness.md`, "Unreachable points": after an `inhale false`, ownership bookkeeping treats the point as
  unreached, emits nothing there, and a merge takes its state from the other edges. Add the new file's tests.
- `SPECIFICATIONS.md`, "Moves": a conditional, `when` or block moves a fresh unique value when every branch yields
  one or leaves abnormally (`throw`, `return`, `break`, `continue`, a call returning `Nothing`).

## G10: contracts of members of instantiated generic classes (S5e)

### Cause

```kotlin
class GenericEcho<E> {
    fun echo(value: E): E {
        postconditions<E> { r -> r === value }
        return value
    }
}

fun lostContract(g: GenericEcho<Any?>, value: Any?) {
    val returned = g.echo(value)
    verify(returned === value)   // fails
}
```

A call on a receiver of type `GenericEcho<X>` resolves to a use-site substitution override of `echo`: a synthetic
FIR function with origin `SubstitutionOverride`, the substituted signature and no body.
`conversion/ProgramConverter.kt`, `embedFormverContract`, reads the specification from `symbol.fir`. For that
symbol `ownSpecification()` finds no body, `userSpecification` returns `null` for a non-source origin before it
looks for `@SpecOf` siblings, and `specificationOwner` finds no overridden declaration with a specification. The
callee header therefore has only its type conditions. The same lookup is why moving the contracts to `@SpecOf`
holders does not help. `@Pure` observers are not affected: `Observers.kt`, `observerDeclaration`, already maps a
call to its declaration with `unwrapUseSiteSubstitutionOverrides()`.

### Encoding

The contract of a use-site substitution override is the contract of the declaration it substitutes:
`embedFormverContract` reads the specification of `symbol.unwrapUseSiteSubstitutionOverrides()`. Everything else
about the callee stays as it is: its name, its header's type conditions, whether it is inlined, pure, or bound
to an external model. The callee header in `lostContract` then carries `ensures ret_1 == value`, and
the calls on `GenericEcho<String>` and `GenericEcho<Int>` get two headers, `c_GenericEcho_f_echo_String` and
`c_GenericEcho_f_echo_Int`, each with its substituted type conditions and the contract.

The specification is converted from the declaration's FIR, so every type-dependent choice in it is the
declaration's: `===` and `==` on `E` keep the kind they have when the member's body is verified (`Dynamic` for an
unbounded `E`), whatever the type argument at the call. The holder's parameters resolve to the callee's
parameters by position, as for `@SpecOf` holders today.

`embedKotlinContract` (Kotlin `contract { }` effects) is not changed; it is outside what the port needs.

### Why it is sound

The member's body is verified once, against its declared header, with `E` erased to its bound. Its contract holds
for every argument that satisfies the erased parameter types. A caller's arguments satisfy the substituted types,
which are subtypes of the erased ones, so asserting the declaration's preconditions and assuming its
postconditions is the ordinary modular rule. The value of an expression of type `E` in the contract is the same
`Ref` in both headers; only its type condition differs.

What the substituted header adds beyond the declared one is its substituted type conditions (`requires` on the
arguments, `ensures` on the result). They are not proved by the body. They are the existing assumption
"Type invariants" in `docs/soundness.md`: a value has its substituted type argument. This change neither adds nor
removes them.

Nothing is newly trusted: the callee header gains a postcondition that the callee's own verification proves.

### Change

One line in `embedFormverContract`, plus its import. The rest of the stage is tests and documentation.

### Tests

New file `verification/classes/generic_member_contracts.kt`:

- Verifying: `lostContract`; `twoInstantiations` (one function calling `echo` on `GenericEcho<String>` and
  `GenericEcho<Int>`, proving each result identical to its argument); a member whose specification is in a
  `@SpecOf` holder, called on an instantiated receiver; a member with a precondition on an `E` argument
  (`preconditions { value !== null }` on `GenericEcho<Any?>`), called with a non-null argument; a `@Unique
  @Borrowed` member of a generic class with an owned `Array<Any?>` field, called on an owned `Box<Int>` receiver,
  whose postcondition `r === elementAt(i)` the caller uses, after which the receiver is still owned and a second
  call verifies.
- Failing siblings: the same caller claiming `returned !== value` fails; the precondition case called with `null`
  fails on the precondition; in the borrowing case, a claim about an index the postcondition does not cover
  (`r === elementAt(i + 1)`) fails.
- Existing: `verification/types/generics.kt` keeps its goldens (it has no member contracts).

### Documentation

- `SPECIFICATIONS.md`, "Preconditions and Postconditions": a call on a receiver of an instantiated generic class
  uses the member's own specification, with its type parameters standing for the call's type arguments.
- `docs/soundness.md`, "Type invariants": the substituted type conditions of a call on an instantiated generic
  class are part of this assumption. Add `generic_member_contracts.kt` to its tests.

## G11: diagnostic markup in the correspondence check (S5f)

### Cause

`PortDeviationTest::real port matches labelled deviations and retained assignment sites` reads
`targets/arraydeque/ArrayDeque.kt` as it is on disk and passes it to `PortDeviationChecker.checkPort`. A
diagnostics test file carries expected-diagnostic markup (`<!VIPER_TEXT!>`, `<!>`, …), which is not Kotlin, so
`parse` fails before any diff is computed.

### Fix

The check compares the upstream file with the source that the test framework compiles: the port with its
markup removed by `org.jetbrains.kotlin.codeMetaInfo.clearTextFromDiagnosticMarkup`, the function the framework
and `VerificationOutcomes` use. `PortDeviationChecker` gains `fun portSource(marked: String): String` that applies
it, and the real-port test passes `portSource(port.readText())`. The strip rule of D2 is unchanged: clearing
markup reconstructs the compiled text and removes nothing from it. Markup inside a string literal is cleared from
the compiled source too, so clearing it in the check keeps the check equal to what is verified.

The diff file is read only when the port exists, and a missing `ArrayDeque.port.diff` fails the test with the
missing path, rather than being treated as an empty list of deviations.

### Tests

In `PortDeviationTest`, with a fixture `portcheck/marked.kt` that is `base.kt` with markup on a class, a function
name and a statement:

- `checkPort(base, portSource(marked), "")` passes.
- `marked.kt` with one body expression changed inside a marked range fails `check` with the unapproved hunk, so
  markup cannot hide an executable change.
- `checkPort(base, marked, "")` without `portSource` fails with the parse error, which pins that the real-port
  test needs the clearing step.

## G12: preconditions of secondary constructors (S5g)

### Cause

```kotlin
class Sized {
    private var data: @Unique Array<Any?>
    @Law @Pure @Unique @Borrowed fun bounded(): Boolean = data.size <= 1073741824
    constructor(capacity: Int) {
        preconditions { 0 <= capacity && capacity <= 1073741824 }
        data = arrayOfNulls(capacity)
    }
}
```

`preconditions` is a no-op call in executed code (`embeddings/callables/FullySpecialKotlinFunction.kt`), and
`embedFormverContract` reads specifications only from a `FirSimpleFunction` or a property getter, so a
`preconditions` block in a constructor is neither assumed in the body nor asserted by callers, and nothing is
reported. Dropping it is conservative: the body proves its obligations without the assumption. It is why the S6
port's `(Int)` constructor, once G9 is fixed, fails "Could not establish class law 'ArrayDeque.representation'
for the result": the capacity law needs `initialCapacity <= 1073741824`. The plan's capacity restriction depends
on that precondition.

### Encoding

A secondary constructor whose body starts with a `preconditions` block has those clauses as the `requires` of its
constructor method, converted against the constructor's value parameters. A clause may read only parameters and
call `@Pure` functions that do not take `this`; a read of `this` or of a property of the class under
construction is rejected with `UNSUPPORTED_FEATURE`, because the object does not exist when the precondition is
evaluated. The body is verified assuming the clauses.

Callers assert the clauses: a constructor call does so through the method call's `requires`. A delegation to a
source constructor (`: this(...)` or `: super(...)`) is inlined by `PrimaryConstruction.kt`
(`initialize(parent, parentArguments)`), so the delegating body asserts the delegate's clauses on the delegation
arguments before the delegate's body runs.

Other specification blocks in construction code are unchanged by this stage: a `postconditions` block in a
constructor, and a `preconditions` block in an `init` block. They stay no-op calls.

### Why it is sound

It is the modular rule for any method: the body assumes what every caller asserts. Callers are constructor calls
(method calls, whose `requires` Viper checks) and inlined delegations (an explicit `assert`). The clauses cannot
read the object, so they mean the same at the call and at the body's entry. Nothing is newly trusted.

### Change

`UserSpecification.holder` and the contract conversion context accept a `FirConstructor` as well as a
`FirSimpleFunction`; `embedFormverContract` takes the leading `preconditions` block of a secondary constructor's
body; the delegation path asserts the delegate's preconditions. About 80–150 lines with the rejection of `this`
reads; conversion only.

### Tests

New file `verification/classes/constructor_preconditions.kt`:

- Verifying: `Sized` above; a client `Sized(10)`; a delegating `constructor() : this(16)`.
- Failing siblings: `Sized(-1)` fails the precondition at the call; `constructor() : this(-1)` fails it at the
  delegation; `Sized` without the `preconditions` block fails the class law, which shows that the block is what
  the body assumes; a clause reading `data.size` is rejected with `UNSUPPORTED_FEATURE`.
- Existing: `verification/soundness/law_free_constructors.kt::InPrecondition` (a `preconditions` block in an
  `init` block) keeps its outcome.

### Documentation

- `SPECIFICATIONS.md`, "Preconditions and Postconditions": a secondary constructor may start with a
  `preconditions` block over its parameters; callers and delegating constructors establish it.

## Feasibility of M after these stages

None of the four gaps points at a deeper problem. G9, G10 and G12 are bookkeeping and contract lookup in code
paths that exist for the statement form, for non-generic receivers and for functions, and the G9 and G10
changes are about 25 lines together. None adds an assumption. The `(Int)` constructor's obligations past G12 are
not yet visible; S6 reports each further gap with a reduced case.

Open points for S6 when it resumes, none of which needs a verifier change on present evidence:

- `last` and `lastOrNull` fail the preconditions of `internalIndex(lastIndex)`. The port's private `lastIndex`
  (D-4) is a `@Pure` getter reading `size - 1`, which reports `INTEGER_OVERFLOW`, and its `@SpecOf("lastIndex")`
  holder is `INVALID_SPEC_OF` (a `@SpecOf` function cannot target a private property). A body that reads the
  private `count`, as the P1 law does, is bounded by the law under the receiver's unfolding; S6 checks that
  before anything else.
- The borrowing-call test of G10 (an owned `Box<Int>` receiver) is the first test of a `@Unique @Borrowed` member
  called on an instantiated receiver. If the uniqueness checker or the signature of the substitution override
  loses the receiver's ownership annotations there, that is a separate gap, found by that test, with its own
  stage.

If a gap cannot be closed without a new assumption, the claim is restricted rather than the trust widened:

- G9: the `(Int)` constructor is written in statement form (an assignment in each `when` arm and a `throw` in the
  last), a form whose ownership the verifier already supports. The rewrite is a body deviation,
  recorded as its own labelled hunk in `ArrayDeque.port.diff`; the claim is then about that form.
- G12: the `(Int)` constructor is removed from the port (a D-7 hunk) and M makes no claim about it; clients
  construct with `()`, whose empty storage meets the capacity law.
- G10: the port's members are proved against their own contracts, since calls inside the class use the
  declarations; client claims through the port (S7's `rotate`) are dropped, and client claims are made only
  through the bundled model of S9, whose binding is checked against the port's contracts by the S9
  spec-equality test.
