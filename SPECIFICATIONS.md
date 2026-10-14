# Writing Specifications in SnaKt

SnaKt translates Kotlin code with formal specifications to [Viper](https://www.pm.inf.ethz.ch/research/viper.html) for verification. This guide assumes familiarity with Hoare logic; see the [Viper tutorial](http://viper.ethz.ch/tutorial/) if needed.

## Verification Control

SnaKt converts and verifies application functions in the sources being compiled. External specification sources are
analysis-only; see [External specifications](#external-specifications). A function's specification comes from its
`preconditions` and `postconditions` blocks, its Kotlin `contract { }`, its ownership annotations, `@Pure`, and its
types:

```kotlin
import org.jetbrains.kotlin.formver.plugin.*

fun divide(numerator: Int, denominator: Int): Int {
    preconditions { denominator != 0; numerator != -2147483648 || denominator != -1 }
    return numerator / denominator
}
```

**Annotations:**
- `@NeverVerify` — do not verify this function or look at its body. Its signature and specification are still
  converted for its callers, and problems in them are reported. docs/soundness.md says what its body is trusted to do.
- `@NeverConvert` — skip Viper conversion entirely

An annotation on a class applies to every declaration in the class: its construction, its members and its nested
classes.

The body of a custom property getter or setter is verified too, against the contract its callers use, which states
nothing beyond the Kotlin types of its receivers, parameters and result. A `@Pure` getter is a pure function instead.
A custom accessor of a top-level property is an unsupported construct.

The constructors, property initializers and `init` blocks of every class are verified as one construction against
the constructor's header. A construction that SnaKt cannot convert, that breaks the construction discipline
(Class laws), or that has a `@NeverVerify` constructor is not verified, and its constructor header gives callers no
field values. The class's other constructors are still verified. A delegated property makes
the construction unconvertible, and a `lateinit` property that construction leaves unassigned breaks the discipline.

A function that uses a construct SnaKt does not translate gets an `UNSUPPORTED_FEATURE` warning at the construct and a
`VERIFICATION_SKIPPED` warning at its name, and is not verified. Compilation continues.

**Plugin configuration** (in `build.gradle.kts`):
```kotlin
formver {
    verificationTargetsSelection("no_targets")  // Convert, but verify nothing
    // or "all_targets" (default) — verify every function
    externalSpec("specs/Library.kt")  // Analyse as a specification of external code, but do not compile it
}
```

## Diagnostics

A verification error means SnaKt could not establish a Kotlin obligation; it does not by itself establish a
counterexample. `VERIFICATION_CONDITION_NOT_ESTABLISHED` identifies explicit Kotlin proof clauses. Backend obligations without a Kotlin classification use `VIPER_VERIFICATION_ERROR` and
include backend error and reason codes. The `errorStyle` option selects Kotlin wording, backend evidence, or both
for the same diagnostic.

Warnings about unsupported conversion or skipped verification indicate incomplete coverage. `UNTRACKED_WRITE`
is a warning because the shared-local write is excluded from the translated state; it is not a proof of that write.
Internal failures, unavailable verifiers and invalid generated programs carry machine-readable reason codes.

The authoritative diagnostic names, severities and source-positioning policies are in
[`FailureKind`](formver.common/src/org/jetbrains/kotlin/formver/common/Failure.kt).
The structured payload, counterexample bindings and typed values are defined in that file;
[`FailureToFirRenderer`](formver.common/src/org/jetbrains/kotlin/formver/common/FailureToFirRenderer.kt)
is the Kotlin diagnostic rendering boundary.

The registry groups user-visible problems as follows:
- Proof conditions: assertions, refutations, laws, preconditions, postconditions, loop invariants and exhaustive
  `when` expressions;
  Kotlin returns effects and conditional effects; collection bounds and sub-list ranges;
  ownership obligations and override refinement. Unclassified backend obligations retain their Silver identifiers.
- Specification declarations: invalid specification functions, external specification bindings, observer bindings,
  observers and laws.
- Locality and uniqueness: incompatible attributes, captures, construction, escape and exit states;
  duplicate or overlapping unique arguments, moved accesses, annotation placement, override and expect/actual
  compatibility, pure results and unique predicates.
- Conversion: impure expressions, unsupported ownership flows or constructs, untracked writes and skipped units.
- Tool failures: internal invariants or unexpected exceptions, verifier availability and invalid generated Viper programs.

Primary locations identify the failing use or implementation. Related locations retain available clause declarations,
ownership-loss origins and counterexample binding declarations.

### Counterexamples

Silicon's mapped model supplies optional Kotlin counterexample bindings for parameters, locals and fields of unique
receivers. `this` identifies a receiver; `old(path)` identifies a value in the function-entry state. A binding retains
its declaration source, displayed as a line and column. Values render as Kotlin integers, booleans, escaped characters,
`null`, or enum entry names. Several enum names separated by `/` identify indistinguishable model values.

Bindings expose only source-attributed values supported by the presentation. Missing models, ambiguous declaration
names, unsupported values, and conditional or quantified predicate snapshots contribute no binding. A model describes
the translated obligation; it can reflect abstraction or missing specifications rather than an executable Kotlin input.

Enable model generation with `formver { counterexamples(true) }` in Gradle, or the compiler-plugin option
`counterexamples=true`. Generation is opt-in because measured model extraction adds substantial verification cost.
[`SiliconFrontend`](formver.compiler-plugin/viper/src/org/jetbrains/kotlin/formver/viper/SiliconFrontend.kt) controls
the backend configuration. Golden-file tests opt in with the test-only `COUNTEREXAMPLES` directive; ordinary goldens record
proof diagnostics independently of solver-selected values. The dedicated
[`counterexamples.kt`](formver.compiler-plugin/testData/diagnostics/verification/counterexamples.kt) fixture constrains
its failing values uniquely.

## Integer arithmetic

`Int`, `Long`, `Short`, `Byte` and `Char` values have their Kotlin finite ranges. Arithmetic is checked in executable code and specification expressions alike, including contracts, laws, loop invariants and `@Pure` bodies. Each evaluated operation must have a representable result; overflow reports `INTEGER_OVERFLOW`. Byte and Short arithmetic uses Kotlin's resolved promotion to Int, while increment and decrement retain the receiver's width.

Division truncates toward zero; remainder has the dividend's sign. A zero divisor reports `DIVISION_BY_ZERO`. `MIN_VALUE / -1` overflows; `MIN_VALUE % -1` is zero. Widening conversions are exact, and narrowing conversions must prove the target range. Char arithmetic and conversions must stay within 0 through 65535. Arithmetic failures are proof obligations, not catchable exits.

## Preconditions and Postconditions

```kotlin
fun abs(x: Int): Int {
    preconditions { x > -2147483648 }
    postconditions<Int> { result ->
        result >= 0
        result == x || result == -x
    }
    return if (x >= 0) x else -x
}
```

Multiple conditions are implicitly conjoined. The postconditions block receives the return value as its parameter.

A condition is a single expression. Besides operators, it may use `if`, `when`, `?.`, `?:` and `as?` for their
values, and call `@Pure` functions and inline functions whose bodies are in the sources being compiled, together
with the lambdas passed to them. Inside such a lambda, a local can be declared and initialized once, and a
`return@label` can end the lambda as its last expression. Anything that needs a statement is reported as an
unsupported construct: loops, assignments to other variables, an `if` or `when` whose value is unused, and calls
to other functions. Supported standard-library scope functions are inlined as described under
Inline functions and default arguments.

### Specifications beside a member

A member without a body, such as an abstract or interface member, takes its specification from a sibling function
annotated `@SpecOf` with the member's name. Any member may be specified this way instead of in its body.

```kotlin
interface Measure {
    @SpecOf("size")
    fun sizeSpec(limit: Int) {
        preconditions { limit > 0 }
        postconditions<Int> { r -> 0 <= r && r <= limit }
    }

    fun size(limit: Int): Int
}
```

The `@SpecOf` function is declared beside its target in the same class or interface, or in the same package for a
top-level `@ExternalFunction` model. Its value parameter types, ownership annotations included, and positional
type parameters match the target; this picks the overload it specifies. It returns `Unit`. An extension receiver
is allowed for an external function model and matches the target's receiver type. Its body holds only a
`preconditions` block followed by a `postconditions` block, whose type is the target's return type. Its parameters stand for the member's parameters
by position. It is never converted, verified or called. A member has at most one `@SpecOf` function, none if its
body has a specification. Each violation, and a call to a `@SpecOf` function, is
reported as `INVALID_SPEC_OF`.

### External specifications

Use external specifications to describe declarations whose implementations are in compiled dependencies. Supply
ordinary Kotlin files as external specification inputs. `@ExternalSpec("package.Type")` on a model interface binds
its members to that dependency type. `@ExternalMember("name")` selects a different target member name.
`@ExternalFunction("package.function")` binds a top-level model function, including an extension function.

```kotlin
package models

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Counter")
interface CounterModel {
    @Pure @Unique fun value(): Int
    @Law @Pure @Unique fun nonnegative(): Boolean = value() >= 0

    @Unique @Borrowed fun advance(step: Int): Int
    @SpecOf("advance") @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step >= 0; value() <= 2147483647 - step }
        postconditions<Int> { result -> result == old(value()) + step; value() == result }
    }
}
```

The model supplies the existing observer, law, ownership and contract machinery. Dependency types in client
signatures use the model's verification representation. Function-model bodies are placeholders; contracts come
from their leading specification blocks or a sibling `@SpecOf` holder. Model declarations are available during
analysis and removed before code generation. Application code references dependency declarations; references to
model declarations are compilation errors.

A member whose model owns its dispatch receiver (`@Unique`, or `@get:Unique` for a property) accepts any receiver. A
call whose receiver is unique just before it gets the model's contract, observers and laws. A call on any other
receiver is the opaque library call: it may throw, and its result is known only through its type. A member declared in
source that owns its receiver accepts only a unique one.

Bindings match callable kind, parameter and result types, generic parameters and bounds, extension receivers,
varargs, property mutability and suspend status. Model-interface types stand for their bound dependency types.
A model interface declares only functions and properties, and `@ExternalMember` appears only inside one.
Unresolved, incompatible, ambiguous and duplicate bindings are errors, each reported as `INVALID_EXTERNAL_SPEC` on
the model declaration; a module with any of them is not verified. Bundled and user specifications share this check. Source subtypes of externally modeled dependency types are rejected because this boundary has no
source-implementation conformance proof.

External contracts, purity, ownership and laws are trusted assumptions about the dependency. SnaKt verifies
callers against them without proving the dependency's implementation. Audit each model against the exact
library version in the compilation classpath; a matching signature alone establishes no behavioral compatibility.
The compiler reports loaded specification inputs and resolved trusted bindings. The bundled assumptions are the
sources indexed by `formver.compiler-plugin/plugin/resources/META-INF/formver/external-specs.list`.
They compile as part of the user's module, so they declare nothing public and raise no warnings: they compile under
explicit API mode and warnings as errors.
In a multiplatform compilation, specification sources belong to the common fragment that every other fragment depends
on, so their models apply in common and platform code alike. When the compilation has no single such fragment they
belong to the platform fragment, and a collection call in common code that needs the collection models is an opaque
library call.

### Overrides

A call to an open member is verified against the contract of the member the call names. Each override is checked
against the contract of every declaration it directly overrides: the overridden precondition must imply the
override's precondition, and the override's postcondition must imply the overridden postcondition. `old` in the
overridden postcondition refers to the state when the override is called. A failure is reported on the override,
naming the declaration whose contract it may not satisfy.

```kotlin
open class Base {
    open fun positive(x: Int): Int {
        preconditions { x > 0 }
        postconditions<Int> { result -> result > 0 }
        return x
    }
}

class Refining : Base() {
    override fun positive(x: Int): Int {
        preconditions { x >= 0 }                       // weaker: accepted
        postconditions<Int> { result -> result > 1 }   // stronger: accepted
        return x + 2
    }
}
```

An override without a specification of its own has the specification of the declaration it overrides, and its body
is verified against it. When several declarations it overrides have one, it has none and must write its own.

The check runs when the override is converted. An implementation left unverified by the plugin's selection or by
`@NeverVerify` is trusted. Each concrete class inheriting a contract-bearing implementation has a separate
obligation that verifies its body and contract refinements under that class's observer bindings, including an
implementation inherited from a superclass when the class adds an interface. This obligation checks the inherited
body even when its declaration has `@NeverVerify`. Abstract intermediate classes carry no implementation harness;
their concrete subclasses carry the obligations.
A `@Pure` function outside the observer mechanism cannot be open or override another; such a function, and a call to it,
is reported as an unsupported construct.

### Observers

An observer is an abstract `@Pure` function or `val` of an interface. A class implements it with a
`@Pure` function body or getter, declared in that class or inherited from a superclass. Open and abstract classes
may participate in the hierarchy, and subclasses may override observer implementations.
Property getters use the same binding, ownership-transfer and law encoding as function observers.
Use `@get:Unique` for a getter that reads mutable owned state.

```kotlin
interface Measure {
    @Pure val size: Int
    @Pure fun scaled(factor: Int): Int
}

class Fixed : Measure {
    @Pure override val size: Int get() = 2
    @Pure override fun scaled(factor: Int): Int = factor * size
}
```

These are reported as `INVALID_OBSERVER`:

- an override of an observer that is not `@Pure`;
- an observer implementation supplied by delegation;
- a class that implements an observer by inheriting a member that is not `@Pure` from a superclass;
- a `@Pure` override of a member that is neither an observer nor an override of one;
- a `@Pure var`.

Observer domains are preconditions supplied by `@SpecOf` (see [Specifications beside a member](#specifications-beside-a-member)).
Calls must establish them, and implementations are checked under the inherited domain. An implementation's own
contract must refine the interface contract. Domain expressions use the same observer dispatch as other observations.

Observer bindings follow the object's runtime class. Interface and overridable class views use bodiless Viper
observer functions; an exact runtime-class fact connects a class view to its registered pure implementation.
A superclass view of a subclass does not expose the superclass's observer body as its runtime interpretation.
Permissions follow the `@Pure` encoding described in
[Pure functions and specifications](#pure-functions-and-specifications). On a shared interface receiver the result
is arbitrary. A consuming or borrowed ownership transfer between class views or between a class and an interface
preserves each observer's value from the pre-transfer snapshot. Function parameters are quantified over the
observer's domain, with the destination observer application as the trigger. Returning a borrowed view preserves
its current observations and runtime binding in the restored view. A bare interface view exposes no class-specific binding.

### Callable specifications

A `fun interface` has one executable abstract operation. Its ghost observers are zero-argument
`@GhostObserver @Pure` functions with the placeholder body `ghostObserver()`. Ghost observers have binary
annotation retention and can be called only in specification blocks or laws. A named class implements them
through the observer implementation and binding mechanism described in [Observers](#observers).
An interface cannot override a ghost observer.

```kotlin
fun interface CounterSpec {
    fun invoke(): Int
    @GhostObserver @Pure fun value(): Int = ghostObserver()
    @GhostObserver @Pure fun step(): Int = ghostObserver()
}

fun counter(start: Int): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == start; step() == 1 }
    start + 1
}
```

`exposes` takes a class-literal specification token and a literal receiver lambda. It is the first statement
of a lambda SAM-converted to that exact interface. Each statement binds an observer on the block's receiver
with `observer() == expression`. Every ghost observer, including inherited observers, is bound exactly once.
Bindings on another receiver, binding values that read a parameter of the lambda, duplicate or missing bindings,
other operators and other statements are reported as `INVALID_EXPOSES`. The block does nothing at runtime; `ghostObserver()` throws if executed.

A literal lambda SAM-converted to a fun interface with ghost observers is lifted to a synthetic class. Its captured
values, receivers and `var`s are fields owned by the class's unique predicate. The lambda body is the class's `invoke`.
Leading `preconditions` and `postconditions` blocks after `exposes` define its own contract; with neither block,
it inherits the interface's `invoke` specification. The body is verified against that contract. The override
refinement check proves that it implements the interface contract under its observer bindings. Callers use the
interface contract. Each binding defines its observer on the class as the bound expression over the
captured state, so an observer follows the lambda's later writes to captured `var`s. The creation site constructs the
object and converts it to an owned value of the interface, carrying each observer's value across.

Creation-path conditions and creator preconditions whose free variables are captured `val`s and whose heap reads
are immutable become assertions over those capture fields in the synthetic class's predicate. Pure helper bodies
must also read immutable state; an abstract helper with owned arguments supplies no stable creation fact.
Bound variables in quantified facts stay local to the fact. The formula is finalized over all creation contexts
before emitting the synthetic constructor and predicate. The constructor requires that formula and returns initialized
fields with their permissions; folding at the creation site proves the capture assertions. The lifted body obtains
them by opening its owned predicate.

### Interface laws

Declare a Boolean `@Law @Pure` interface member with a body to state a law. Laws have no value, type, extension,
or context parameters and cannot be overridden. Their bodies admit immutable locals, value operations, observers
on `this`, and `@Pure` top-level or companion helpers; assignments and mutable locals are rejected with
`INVALID_LAW`. Use `@Unique` when a law calls observers that require an owned receiver:

```kotlin
interface Sized {
    @Pure @Unique fun size(): Int
    @Law @Pure @Unique fun nonnegative(): Boolean = size() >= 0
}
```

Every interface has an abstract footprint predicate. Its ownership predicate contains that footprint, the runtime
subtype fact, and the conjunction of zero or more laws, including inherited laws. Observer functions require the
footprint; owned calls unfold the ownership predicate to supply it. An owned interface client can use the laws
without a separate precondition. Inheritance diamonds include each law once.
Implementation law harnesses prove inherited laws from the class's own ownership predicate, with its observer
bindings substituted. They check arbitrary legal representation states; a failure is reported on the class. Law declarations are specification bodies and have no standalone verification obligation.

### Class laws

A class may declare `@Law @Pure` Boolean members with the signature and expression rules of interface laws.
Its unique predicate conjoins these representation invariants with its field permissions. Observer calls in class
laws use the declaring class's implementation, including inherited bodies, expanded recursively to field reads.
Field reads and `@Pure` non-member calls retain their ordinary encoding. An observer without a visible body or a
recursive expansion is rejected with `INVALID_LAW` and a reason.

Class laws must hold whenever an object is reachable through a shared path. A mutable field is **law-bound**
when its declaring class's own laws read it after observer expansion, or its declaration carries `@LawBound`.
Shared-path writes to law-bound fields are rejected with `UNSUPPORTED_FEATURE`; this applies to nested owned
children according to the child's field declaration. A shared-path write includes a call of a custom setter and the
setter's own write to `field`. `@LawBound` protects storage without asserting a law. A class law may not read a
property with accessor methods, which is an open property or a `var` with a custom setter; such a read produces
`INVALID_LAW`.
A class law may read its own fields and inherited `val` fields. It may read an inherited `var` only if that field
is law-bound in its declaring class; other inherited `var` reads produce `INVALID_LAW` with a pointer to this
section. This protection applies across superclass and subclass views.

Constructor initialization has two phases. Phase one lasts until Kotlin definite assignment establishes every
property of the declaring class. During this phase, construction may initialize backing fields and read its own
non-open stored properties that every path has already assigned, as in `class C(var a: Int, val b: Int) { var sum =
a + b }`. It may not otherwise read, pass, capture, or call on `this`. After definite assignment, own-field reads and writes, checks, local computation,
and calls without `this` or a member reference may execute before establishment. The proof establishes the
declaring class's laws without assuming them at the first exposure of `this`, or at constructor end, whichever
comes first. Throws before establishment are abnormal exits. After establishment, ordinary ownership operations
preserve the constructed representation. In a non-escaping constructor, phase-two `this` is unique.
During construction, an open class may call on `this` only members that no subclass overrides, and the bodies of
those members are held to the same rule. It may not pass `this` to another function or call an extension on it.

Constructor proofs allocate fresh field permissions, execute delegated construction, and execute property
initializers and init blocks in declaration order. Secondary constructors execute phase-two bodies after delegation.
The returned representation and its opened-predicate description include the invariants. A violating proof is a
verification error on the class. Delegated properties in law-bearing classes are rejected with `INVALID_LAW`.

A construction-discipline violation is reported with its reason: `INVALID_LAW` for classes with own or inherited
class laws, and `VERIFICATION_SKIPPED` for other classes. Callers of such a constructor still assume the class laws.

Unfolding an owned class receiver exposes its invariant to the member body. Every refold proves the invariant,
including refolds for calls and control-flow joins. On member exit, a retained unique receiver with class laws is
refolded even when the receiver is consumed rather than borrowed. Ownership transfers prove the invariant before
moving the representation. An invariant violation is a verification error.

For an owned receiver, a concrete observer's function definition reads its pure body under an unfolding of the
class's unique predicate. The interface-law harness connects the invariant's expanded field expression to the
concrete observer through that definition. It proves inherited interface laws from the class invariant without
an additional observer axiom.

## Loop Invariants

```kotlin
fun sumUpTo(n: Int): Int {
    preconditions { n >= 0 }
    var sum = 0
    var i = 0
    while (i <= n) {
        loopInvariants {
            i >= 0
            sum == i * (i - 1) / 2
        }
        sum += i
        i++
    }
    return sum
}
```

The rules are as follows:
- Loop invariant must hold when the loop is entered.
- The loop body may assume the condition holds.
- Loop invariant must hold after each iteration.
- Loop invariant must hold when the loop is exited.
- Code after the loop may assume the condition fails.
- Of the properties of unique objects the loop reads or writes, including through functions and lambdas inlined into
  it, code after the loop knows only what the invariant says. What was known about the other unique objects still
  holds.

A `for` loop over an `Int` progression written with `until`, `..<`, `..` or `downTo`, optionally followed by
`step`, puts its `loopInvariants` at the start of its body:

```kotlin
fun fill(arr: @Unique @Borrowed IntArray, v: Int) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == v) }
    }
    for (i in 0 until arr.size) {
        loopInvariants {
            forAll<Int> { k -> (0 <= k && k < i) implies (arr[k] == v) }
        }
        arr[i] = v
    }
}
```

The ends and the step are evaluated once, before the loop. In the invariants, the loop variable holds the value of
the coming iteration, and after the last iteration the value one step past it; it stays at the start when the
range is empty. When the value one step past the last element is not an `Int`, as in `for (i in 0..Int.MAX_VALUE)`,
the loop leaves after the last iteration without stepping, as Kotlin does, and the invariants are not checked after
it. The body may assume the variable lies within the range. A step that is not positive throws.

## Universal Quantification

Use `forAll<T>` for quantified formulas:

```kotlin
fun example(arr: @Unique @Borrowed IntArray): Unit {
    preconditions {
        forAll<Int> { j ->
            (0 <= j && j < arr.size) implies (arr[j] > 0)
        }
    }
    // ...
}
```

The `implies` infix operator is provided for convenience (`a implies b` ≡ `!a || b`).

### Triggers

A quantifier without `triggers()` gets its triggers inferred by Viper, unless its
body contains `old(...)`. Viper's inference then tends to keep only the term
under `old`, so the quantifier fires only where the old value is already known,
and a fact about the current state at a new index cannot use it. For such a
quantifier SnaKt derives the triggers itself: each largest current-state term
that mentions the bound variable and is built only from property reads, array
element reads, sizes and function calls becomes a trigger. In

```kotlin
forAll<Int> { k -> (0 <= k && k < arr.size && k != i) implies (arr[k] == old(arr[k])) }
```

the trigger is `arr[k]`. A term whose index is arithmetic, such as `arr[k + 1]`,
cannot be a trigger, so `forAll<Int> { k -> arr[k + 1] == old(arr[k]) }` has no
current-state trigger to derive and keeps the inferred `old` one; write
`triggers(...)` for it when a caller needs it.

You can specify triggers explicitly; explicit triggers replace both the derived
and the inferred ones:

```kotlin
forAll<Int> { x ->
    triggers(x * x)  // Single trigger
    x * x >= 0
}

forAll<Int> { x ->
    triggers(x * x, x + 1)  // Multiple triggers
    x != 0 implies (x * x > 0)
}
```

Each argument to `triggers()` becomes a separate trigger. This differs from Viper syntax where you can group multiple expressions in a single trigger; currently SnaKt only supports simple (single-expression) triggers.

## Ownership

SnaKt reasons about mutable heap data through ownership. A value is either
*unique*, meaning one path owns it, or *shared*. Default `val` properties are
immutable and need no annotations. A `var` property, or an `IntArray` element,
is read and written for real only through a unique path.

### Annotations

`@Unique` and `@Borrowed` annotate types, so they are written on the type:
`n: @Unique Node`, `fun f(): @Unique Node`, `var next: @Unique Node?`,
`fun @Unique Node.e()`. On a member function they describe the dispatch
receiver `this`: `@Unique @Borrowed fun track(line: String)`. A member bound by an external
specification accepts any receiver; see [External specifications](#external-specifications).

| Site | Annotation | Meaning |
|:-----|:-----------|:--------|
| parameter or extension receiver | none | Shared. The function owns nothing. |
| parameter or extension receiver | `@Borrowed` | Shared, and the function may not store, return or capture the value. |
| parameter or extension receiver | `@Unique` | The caller hands the value over. The argument cannot be used after the call. |
| parameter or extension receiver | `@Unique @Borrowed` | The function owns the value during the call and gives it back. |
| function result | `@Unique` | The caller receives ownership of the result. |
| `var` or `val` property | `@Unique` | The object owns the property's value whenever the object itself is owned. |
| local | `@Unique`, or inferred | The local owns its value. |
| dispatch receiver `this` | on the member function: none, `@Borrowed`, `@Unique` or `@Unique @Borrowed` | As for a parameter. |
| class | `@Manual` | Permissions for the class are folded by hand. |
| `@Pure` function parameter or `this` | `@Unique` | Borrowed, as `@Unique @Borrowed`. |

```kotlin
class Node(val value: Int, var next: @Unique Node?)

fun consume(n: @Unique Node) { }
fun inspect(n: @Unique @Borrowed Node) { }
fun make(v: Int): @Unique Node = Node(v, null)
```

An inferred local is unique exactly when its initializer is a unique path or a
call to a function returning `@Unique`. A constructor call does not count, so
a fresh object that should be owned needs the annotation:

```kotlin
val a = make(1)                   // unique
val b = Node(2, null)             // shared: writes to b.next are dropped, with a warning
val c: @Unique Node = Node(3, null)  // unique
```

A constructor call gives a shared object, and no facts about its fields, when the class lets `this` escape
while it is constructed. That happens when a constructor body, an `init` block
or a property initializer, of the class or of a superclass, uses `this` in executable code other
than to read or write a field, to pass it to a `@Borrowed` parameter, or to
call a member that borrows `this` (`@Borrowed`, `@Unique @Borrowed` or
`@Unique @Pure`): for example, it calls an unannotated member function, reads
an open property or one with a custom getter, stores `this`, or captures it in
a lambda that is not inlined. Such a class compiles as usual, but its objects
cannot be owned:

```kotlin
class Parser(first: String) {
    private var depth = 0
    init { track(first) }        // member call on `this`
    private fun track(s: String) { depth += s.length }
}

val p: @Unique Parser = Parser("x")  // error: the construction of 'Parser' calls member 'track' on 'this'
```

During non-escaping construction, initialized stored unique fields are owned paths.
Construction phases and `this` ownership are described in Class laws. A final `@Unique @Borrowed` or
`@Unique @Pure` member may borrow the phase-two receiver.
A constructor call gives the type facts of every field. It also gives the value a parameter stores in a property,
unless construction code assigns that property or, for a `var`, uses `this`
other than to read or write a field. An argument stored in a `@Unique`
property keeps what the caller knew about it only when the constructor carries
no class laws, construction code neither reads nor assigns that property, and
uses `this` only to read or write fields.

`@Unique` on a value type (`Int`, `Boolean`, `Char`, `String`, `Unit`, or their
nullable forms) is rejected, since such values own nothing.

### Unique and shared paths

Through a unique path, `var` reads and writes are real, and the verifier
tracks their values. Through a shared path, every `var` read yields an
arbitrary value. Shared `var` writes follow the law-bound field rule in Class laws;
other shared `var` writes are dropped from the specification, because other code
may hold the same object.

```kotlin
class Counter(var n: Int)

fun bump(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.n == old(c.n) + 1 }
    c.n = c.n + 1                 // verifies
}

fun reset(c: Counter) {
    c.n = 0
    val k = c.n
    verify(k == 0)                // fails: c is shared
}
```

After a call that passes an owned value to a `@Borrowed` parameter, the
caller forgets what it knew about that value's `var` properties, since the
callee may have written them. A function-type parameter can only be
`@Borrowed`, so a call of a function object forgets this for every owned
argument.

### Moves

Passing a unique path to a `@Unique` parameter, assigning it, or returning it
moves it. A moved path cannot be used until it is assigned again. Moving a
property out of an object leaves a hole in it, and the object cannot be
passed on or returned while the hole is there:

```kotlin
fun detach(b: @Unique Node): @Unique Node? {
    val rest = b.next             // moves b.next out of b
    b.next = null                 // fills the hole
    consume(b)
    return rest
}
```

### Inline functions and default arguments

An inline function whose body is in the sources being compiled is verified as
its body inlined at each call, and its parameters keep the meaning of their
annotations there. Writes through a `@Unique @Borrowed` parameter are real,
and the caller knows the values written. A `@Borrowed` parameter whose
argument the caller owns, a variable or a property path such as `b.next`, is
treated the same way. A lambda the inline function invokes must not assign a
path the function borrows; that is reported as unsupported ownership.

A lambda passed to an inline function owns a parameter it annotates, as in
`{ c: @Unique Counter -> ... }`. A parameter without annotations is shared,
whatever the inline function passes to it.

```kotlin
inline fun setN(c: @Unique @Borrowed Counter, v: Int) {
    c.n = v
}

fun caller(c: @Unique @Borrowed Counter) {
    setN(c, 5)
    verify(c.n == 5)              // verifies
}
```

The standard-library scope functions `let`, `also`, `apply`, `run` (with or
without a receiver), and `with` inline a literal lambda, or an inline parameter
bound to one. Kotlin's calls-in-place analysis supplies the lambda's ownership
facts. The receiver is evaluated once before the lambda. `let`, `run`, and
`with` return the lambda's result; `also` and `apply` return the receiver.
Assertions, exceptions, and labelled returns in the lambda are verified in the
caller's flow.

A default argument may read `var` properties through a `@Unique` parameter of
its function, as in `fun show(c: @Unique Counter, k: Int = c.n)`.

### Collection iteration

`IterationSequence<T>` is an immutable, specification-only sequence. It is
available in specifications and `@Pure` functions. `iterationSequenceOf(...)`
constructs one; `size` gives its length, `[i]` reads an element with an in-bounds
obligation, and `in` tests membership. Equality compares the elements in order.
`take(n)` gives the prefix of length `n` clamped between zero and the sequence's
size. Elements may be primitive values, references, or null.

`iterationSequence(collection)` reads the abstract iteration observer of an
owned `Collection`; for a `List`, its length and elements agree with `size` and
indexed access. An `Iterable` that is not a `Collection` may have more than
`Int.MAX_VALUE` elements, more than an `Int` size can count, so SnaKt rejects
its observer and the templates below on it. For a `Set`, the sequence is duplicate-free and its membership
agrees with `contains`. `iterationSequence(map)` gives entries aligned with a
duplicate-free keys sequence whose membership agrees with `containsKey`;
entry values agree with lookup at those keys. Set and Map iteration order has
no guarantee. `Map.Entry.key` and `value` are immutable observations that
require no ownership of the entry.

The inline collection templates `forEach`, `forEachIndexed`, `map`, `filter`,
`any`, `all`, `none`, `count`, `indexOfFirst`, `firstOrNull`, and `fold` use this observer
for `Collection` receivers. The supported Map overloads are `Map.forEach` and
`Map.map` with a single immutable-entry lambda parameter. A literal
lambda, or an inline parameter bound to one, is verified through the inline
call path with Kotlin's calls-in-place ownership analysis. A search whose
lambda is a single pure expression preserves the predicate at its witness
and its negation before that witness. `count` is bounded by the collection
size. `fold` carries its accumulator through the loop and returns it.
`map` and `filter` return an owned List described by the computed iteration
sequence. A single pure `map` expression determines every result element at
its corresponding source index. A single pure `filter` predicate holds for
every result element; its size lies between zero and the source size. A
parameter-independent predicate also determines the size exactly. For
element-dependent predicates, completeness and source order are not modelled.
The trusted allocation boundary is registered in docs/soundness.md, "Collection builder
allocation".

Ordinary iterator-based collection `for` loops are unsupported; the templates
provide collection iteration with prefix invariants.

At the start of a collection-template lambda,
`loopInvariants<E> { prefix -> ... }` binds `prefix` to the iteration sequence
of elements already processed. It is empty on entry and contains every
visited element on exit; a search stops after visiting its witness. In a
`fold` lambda, the accumulator parameter names the current accumulator in
these invariants. The element parameter is unavailable at the loop head.
For example:

```kotlin
var visited = 0
values.forEach {
    loopInvariants<Int> { prefix -> visited == prefix.size }
    visited++
}
verify(visited == values.size)
```

### Exceptions

Verification establishes postconditions and Kotlin return effects on normal
return. `throw`, failed `require` and `check`, `error`, `!!` on null,
failed `as` casts, a read of an unassigned `lateinit` property, and a null
platform-typed value assigned to a declared non-null type leave abnormally.
Their operands are evaluated first.
Runtime casts and `!!` in specifications and `@Pure` bodies are unsupported.

A `when` that Kotlin accepts as exhaustive without an `else` branch throws when no branch matches. SnaKt proves
instead that a branch matches, from the branch conditions and the subject's type: a value of an enum class is one of
its entries, a value of a sealed class or interface is a value of one of its direct inheritors, and a `Boolean` is
`true` or `false`. Comparing the subject with an `object` does not establish a match, since SnaKt does not identify
the values of an object type.

An ordinary impure call may leave exceptionally after its arguments are
evaluated, before its normal-return facts apply. Shared mutable reads are
unconstrained, including on exceptional paths. A catch receives the union of
states at possible throw sites. Catch type and source-order selection are
overapproximated: any enclosing catch can receive an exception, and an
exception can escape the function. The catch parameter is an arbitrary value
of its declared type. Ownership restrictions are listed under Rejected constructs.
A `finally` block runs on normal completion and before a pending return,
throw, break, or continue leaves its `try`/`catch`. It receives the union of
states on those paths. A return value or `try` expression value is captured
before the finalizer runs. An abrupt completion in the finalizer replaces the
pending completion; nested finalizers run from the innermost outward.

Array and string index bounds, division by zero, and integer overflow remain
verification obligations inside `try`. The checked arithmetic fragment is
described under Integer arithmetic. Verification does not establish freedom
from exceptions in opaque library operations or from JVM resource, linkage,
or asynchronous failures.

### Folding

SnaKt manages permissions to the properties of unique objects automatically:
reads and writes, calls, loops and returns need no `fold` or `unfold`. On a
class marked `@Manual`, permissions are managed by hand:

```kotlin
@Manual
class Cell(var value: Int)

fun set(c: @Unique @Borrowed Cell) {
    unfold(UniquePred(c))
    c.value = 5
    fold(UniquePred(c))
}
```

### `IntArray`

An `IntArray` holds its elements as a sequence.

- `arr.size` needs no ownership and works on any array. It is never negative.
- `arr[i]`, `arr[i] = v`, `arr.get(i)`, `arr.set(i, v)`, `arr[i] += v` and
  `arr[i]++` read and write the elements of a unique array.
  None of them moves the array.
- On a shared array, an element read yields an arbitrary `Int` and an element
  write is dropped. All element accesses check the index bounds, including
  discarded reads. In a specification, an element read needs a unique array.
- `IntArray(n)` creates an array of `n` zeros. As with any constructor call, a
  local holding it owns it only when declared `@Unique IntArray`.
- `IntArray(n) { init }` runs `init` on each index from `0` to `n - 1` in
  order, inlined into a loop. When the body of `init` is a single pure
  expression, every element is known to equal it at its index afterwards;
  otherwise only the size is known. The loop keeps ownership of the unique
  data around it, and keeps facts about that data as any loop does (see Loop
  Invariants).

```kotlin
fun swap(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> { arr[i] == old(arr[j]) && arr[j] == old(arr[i]) }
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}
```

### Multisets

A `Multiset<Int>` states which elements a collection holds, counted with
multiplicity and in no order. It exists only in specifications and `@Pure`
functions. `multisetOf(...)` builds one, and `contents(arr)` gives the elements
of a unique `IntArray`. The operators `+`, `-`, `count`, `in` and `size` are
described in `Builtins.kt`. Any element type other than `Int` is a compile
error.

A postcondition `contents(arr) == old(contents(arr))` says the function only
permuted the array. Together with a sortedness condition it specifies a sort.
For a linked structure, write a recursive `@Pure` function that returns a
`Multiset<Int>`.

```kotlin
fun swap(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> { contents(arr) == old(contents(arr)) }
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}
```

The verifier relates `contents(arr)` to individual elements only at element
reads it already knows of. A quantified fact such as "every element equals
`v`" does not prove `v in contents(arr)` on its own. A statement naming one
element, such as `verify(arr[0] == v)`, supplies that read.

### Pure functions and specifications

Specification blocks (`preconditions`, `postconditions`, `loopInvariants`,
`verify` and their contents) and calls to `@Pure` functions borrow their
arguments: they move nothing. An inline function called in a specification
borrows its arguments too, so `c.let { it.x }` may read a `@Borrowed` `c`. A `@Pure` function may read `var` properties
only through its `@Unique` parameters, and specifications may read them only
through unique paths.

Specifications, laws and `@Pure` function bodies evaluate `&&`, `||` and `if`
as Kotlin does. The preconditions and reads in the right operand of `&&` need
to hold only where the left operand is true, in the right operand of `||` only
where it is false, and in a branch of an `if` only where the condition selects
that branch. A law `size <= 0 || at(0) != null` needs the precondition of `at`
only when `size > 0`. The infix `and` and `or` evaluate both operands, so
`(b != 0) and (a / b > 0)` needs `b != 0` for its division.

A `@Pure` function cannot return an alias into data its caller still owns. Its
result cannot be `@Unique`, and when it has a `@Unique` parameter its result
must be a value type.

```kotlin
@Pure
fun isSorted(n: @Unique Node?): Boolean {
    preconditions { decreases(n) }
    if (n == null) return true
    val m = n.next
    return if (m == null) true else n.value <= m.value && isSorted(m)
}
```

In a postcondition, a parameter the function consumed has no readable `var`
properties. Its default `val` properties and its identity remain readable.

The verifier expands a recursive `@Pure` function's definition one level at
each application it sees. When a proof needs a deeper level, the body must
mention that application itself. Here `size(r) == old(size(t))` verifies only
because the body names `size(right)` before the move and `size(t)` after it:

```kotlin
@Pure
fun size(t: @Unique Tree?): Int {
    preconditions { decreases(t) }
    return if (t == null) 0 else 1 + size(t.l) + size(t.r)
}

fun rotateLeft(t: @Unique Tree): @Unique Tree {
    postconditions<Tree> { r -> size(r) == old(size(t)) }
    val right: @Unique Tree? = t.r
    if (right == null) {
        t.r = right
        return t
    }
    val sr = size(right)
    t.r = right.l
    val st = size(t)
    right.l = t
    return right
}
```

### Termination of pure functions

A recursive `@Pure` function must declare a termination measure with
`decreases(...)` in its `preconditions` block. Without one, a definition such
as `loopy(x) == loopy(x) + 1` would let callers prove `false`. A function is
recursive when it can reach itself through calls in its specification or body,
directly, through other functions, or through the laws of a class whose
predicate it unfolds. Non-recursive functions need no measure.

```kotlin
@Pure
fun ackermann(m: Int, n: Int): Int {
    preconditions {
        m >= 0
        n >= 0
        decreases(m, n)
    }
    return if (m == 0) n + 1 else if (n == 0) ackermann(m - 1, 1) else ackermann(m - 1, ackermann(m, n - 1))
}
```

Each argument of `decreases` is a measure, and several measures are compared
lexicographically. A measure is one of:

- An `Int`, ordered as a natural number: it must decrease and stay
  non-negative at each recursive call.
- A reference to an object of a class, not an interface, ordered by the nesting
  of its unique predicate. A call on a field of a `@Unique` parameter, as in
  `length(n.next)`, decreases it. A nullable reference also decreases when the
  call passes `null`.

Each recursive call, including the calls of mutually recursive functions, must
decrease the measure; the verifier reports `TERMINATION_NOT_ESTABLISHED` at a
call where it does not. A recursive function with no measure is reported as
`MISSING_TERMINATION_MEASURE`, together with the call cycle, and is not
verified; its callers see no definition for it. `INVALID_TERMINATION_MEASURE`
rejects `decreases` on a function that is not `@Pure`, a second `decreases` in
one function, and a measure of any other type.

Recursion over shared objects has no measure: a shared node can reach itself
through its `next` chain, so `length(n.next)` need not be smaller.

### Rejected constructs

Each of these is a compile error:

- A downcast move: `val s: @Unique Sub = base as Sub`.
- A lambda, anonymous object, local class or local function that captures an
  owned root, unless Kotlin inlines it in place:
  `val f = { consume(n) }`.
- `@Unique` on a declaration whose type is a type parameter:
  `class Box<T>(var item: @Unique T)`.
- An override that does not repeat the `@Unique` and `@Borrowed` annotations
  of the declaration it overrides, on parameters, receivers and result.
- An `actual` declaration that does not repeat the `@Unique` and `@Borrowed`
  annotations of its `expect` declaration, on parameters, receiver and result.
- A default value that aliases a `@Unique` parameter, or that is itself
  `@Unique` and aliases another parameter: `fun f(c: @Unique Node, d: Node = c)`.
- `try` in a function that owns anything anywhere: a signature with `@Unique`
  or `@Borrowed`, or any owned local, in or outside the `try`. Passing a fresh
  object straight to a call, as in `try { consume(Node(1, null)) }`, is
  allowed.
- Using a unique path after an accessor call on it, or after a call to a
  member that does not borrow `this`: `n.describe()` consumes `n` unless
  `describe` is `@Borrowed`, with or without `@Unique`.
- `@Unique` or `@Borrowed` on a function without a dispatch receiver.
- Using a list after walking it with a cursor. A step `p = p.next` moves the
  node, so the loop consumes the list:

  ```kotlin
  var p: @Unique Node? = head
  while (p != null) { p = p.next }
  return head                     // error: head was moved
  ```

Open properties, overriding properties and properties with a custom getter
or setter are calls, not fields. They carry no stable facts, and reading one
through an owned path consumes that path.

## Exceptions boundary

Resource exhaustion is outside the verification safety claim. String values have at most `Int.MAX_VALUE` UTF-16 code units. Executable concatenation whose combined length exceeds that limit takes an abnormal exit; on normal return its length is the sum. Pure and specification concatenation requires no size precondition: its result is a bounded String, with exact concatenation semantics guarded by the combined length fitting within the limit. Above that limit the result is unspecified.

## Additional Plugin Options

```kotlin
formver {
    errorStyle("user_friendly")  // or "original_viper", "both"
    logLevel("only_warnings")    // or "short_viper_dump", "full_viper_dump"
}
```
