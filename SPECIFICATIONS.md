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

Every value has its static Kotlin type. A value of a type bound to an external-specification model also has that
type's Kotlin supertypes. A platform-typed value, such as the result of a Java call, may be null. Used as the receiver
of a call or property read, as an argument or vararg element for a non-null parameter (including a default and a
`super` constructor argument), as a comparison operand, as a returned value, or as an initializer or assigned value of
a non-null type, a null platform-typed value throws, and evaluation otherwise continues with a value of the expected
type. A receiver or explicit argument of a non-primitive type throws once every receiver and explicit argument of its
call or write, including the assigned value, is evaluated, where the call or write would happen; one of a primitive
type throws as it is evaluated. As a condition, or as an operand of an array, range, `StringBuilder`, `Map.Entry`
component or collection higher-order operation that SnaKt translates directly, it is a possibly null value.

The body of a custom property getter or setter is verified too, against the contract its callers use, which states
nothing beyond the Kotlin types of its receivers, parameters and result. A `@Pure` getter is a pure function instead.
A read of a top-level property with a custom getter calls the getter, and a write to a top-level property with a
custom accessor calls the setter. A default setter of such a property is a method without a body, so later reads do
not know the written value. An accessor of a top-level property that reads or writes `field` is an unsupported
construct.

The constructors, property initializers and `init` blocks of every class are verified as one construction against
the constructor's header. A construction that SnaKt cannot convert, that breaks the construction discipline
(Class laws), or that has a `@NeverVerify` constructor is not verified, and its constructor header gives callers no
field values. The class's other constructors are still verified. A delegated property makes
the construction unconvertible, and a `lateinit` property that construction leaves unassigned breaks the discipline.

A function that uses a construct SnaKt does not translate gets an `UNSUPPORTED_FEATURE` warning at the construct and a
`VERIFICATION_SKIPPED` warning at its name, and is not verified. Compilation continues.

A call that SnaKt translates without knowing what it computes gets an `OPAQUE_CALL` warning, and the function is
still verified. The call may throw, and its result is some value of its type. These are:
- a call of a library function, or a read of a library property, that SnaKt gives no model: no built-in
  translation, external specification, library contract or Kotlin contract. Library operators are calls too;
- a read of a top-level property other than a `const val`, a `@Pure` property with a getter body or a property with
  a custom getter, and a read of a Java field.

A write to a Java field is dropped, as a write through a shared path is (see
[Unique and shared paths](#unique-and-shared-paths)); a later read gives an arbitrary value. An access of a Java field
through a null receiver of platform type throws.

A read of a local or final delegated property calls its delegate's `getValue`, and a write calls `setValue`, under
that function's contract; with none, the call is opaque. The delegate of a property that is not local is an arbitrary
value of its type, since construction, which sets it, is not converted. Other delegated properties are read and
written through their accessors.

`x::class` evaluates `x` and gives an arbitrary non-null value of its static type; `C::class` gives an arbitrary
non-null value of its type.

A constructor of a library class and a function returning `Nothing` get no such warning.

Such a library call or read is listed in the trust report. A standard-library member without a model counts as one
even when its receiver or result is a collection or string that has a model; only the members a model declares get
its contract. The call passes its receiver and arguments as the library declaration's parameters do: an owned value
passed to a parameter without annotations is moved. A final standard-library member whose result and parameters are
all primitives, `String`, `Unit` or `Nothing`, that has no context parameters, no receiver of a function type and some
receiver that can be owned (one that is none of those types, an array or an object declaration), instead borrows its
receivers, on the trusted assumption that it keeps no reference to them; the trust report lists it as a non-retaining library call. After such a call, every object the
caller owns that the call can reach through the receiver keeps only its class invariants and laws, and a model's
observers on it are unknown:

```kotlin
fun keepsOnlyInvariants(): Int {
    val list = mutableListOf<Int>()
    list.add(4)
    val total = list.sum()           // OPAQUE_CALL; borrows list
    val before = list.size
    verify(before >= 0)              // verifies: a law of the model
    list.remove(total)               // list is still owned and keeps its model
    return list.size
}

fun reliesOnChangedState() {
    val list = mutableListOf<Int>()
    list.add(4)
    list.sum()
    verify(list.size == 1)           // fails: sum may have changed list
}
```

Select compilation checks and external specification inputs through the
[configuration reference](docs/configuration.md).

## Diagnostics

A verification error means SnaKt could not establish a Kotlin obligation; it does not by itself establish a
counterexample. `VERIFICATION_CONDITION_NOT_ESTABLISHED` identifies explicit Kotlin proof clauses, and
`THROWS_CONDITION_NOT_ESTABLISHED` a `throwsOnlyIf` condition (see [Exceptions](#exceptions)). The `EXCEPTION_FROM_*`
and `EXCEPTION_IN_TRY` errors mark an exception that may leave a function checked under
[Exception freedom](#exception-freedom). Backend obligations without a Kotlin classification use `VIPER_VERIFICATION_ERROR` and
include backend error and reason codes. The `errorStyle` option selects Kotlin wording, backend evidence, or both
for the same diagnostic.

Warnings about unsupported conversion, skipped verification or opaque calls indicate incomplete coverage. `UNTRACKED_WRITE`
is a warning because the shared-local write is excluded from the translated state; it is not a proof of that write.
`UNCHECKED_LIBRARY_CALL` marks a call that is the opaque library call because it does not own the receiver its
external specification owns (see [External specifications](#external-specifications)).
Internal failures, unavailable verifiers and invalid generated programs carry machine-readable reason codes.

The authoritative diagnostic names, severities and source-positioning policies are in
[`FailureKind`](formver.common/src/org/jetbrains/kotlin/formver/common/Failure.kt).
The structured payload, counterexample bindings and typed values are defined in that file;
[`FailureToFirRenderer`](formver.common/src/org/jetbrains/kotlin/formver/common/FailureToFirRenderer.kt)
is the Kotlin diagnostic rendering boundary.

The registry groups user-visible problems as follows:
- Proof conditions: assertions, refutations, laws, preconditions, postconditions, `throwsOnlyIf` conditions, loop
  invariants and exhaustive `when` expressions;
  Kotlin returns effects and conditional effects; collection bounds and sub-list ranges;
  ownership obligations and override refinement. Unclassified backend obligations retain their Silver identifiers.
- Specification declarations: invalid specification functions, external specification bindings, observer bindings,
  observers and laws.
- Locality and uniqueness: incompatible attributes, captures, construction, escape and exit states;
  duplicate or overlapping unique arguments, moved accesses, annotation placement, override and expect/actual
  compatibility, pure results and unique predicates.
- Conversion: impure expressions, unsupported ownership flows or constructs, untracked writes and skipped units.
- Tool failures: internal invariants or unexpected exceptions, verifier availability and invalid generated Viper programs.

Primary locations identify the failing use or implementation. A postcondition of an impure function that does not
hold is reported at the `return` it fails on, or at the function body when the body ends without one. An impure
`verify` or `refute` condition of an inline function in another file is reported at the call that inlines it, with the
condition as a related location. Related
locations retain available clause declarations, ownership-loss origins and counterexample binding declarations. When
the failing clause of a contract lies apart from the primary location, the message quotes it with its line and column.
A loop invariant failure says whether the invariant was not established on entry, not preserved by an iteration, or
not established where the loop exits. A class law failure names the law and the owned path it was checked for.
Integer operations are spelled as Kotlin operators, and an operation whose result may fall outside its range is
reported once.

### Counterexamples

Silicon's mapped model supplies optional Kotlin counterexample bindings for parameters, locals and fields of unique
receivers. `this` identifies a receiver; `old(path)` identifies a value in the function-entry state. Parameters cannot
change, so a parameter's own entry value is not shown; entry values of fields reached through it are. A failure at
function exit, a postcondition or a returns effect, binds the returned value as `result`. A `List` or other collection
binds its size as `xs.size`, and a [primitive array](#primitive-arrays) or `Array<T>` as `arr.size`, through any reference. Bindings follow declaration order,
with `this` first and `result` last; entry-state bindings follow the failing-state ones. A binding retains its
declaration source, displayed as a line and column. Values render as Kotlin integers of every width, booleans, escaped
characters, `null`, or enum entry names. Several enum names separated by `/` identify indistinguishable model values.

Bindings expose only source-attributed values supported by the presentation. Missing models, ambiguous declaration
names, unsupported values, and conditional or quantified predicate snapshots contribute no binding. A model describes
the translated obligation; it can reflect abstraction or missing specifications rather than an executable Kotlin input.

A solver can return a model from a query it could not decide, such as one with nonlinear arithmetic. For
`INTEGER_OVERFLOW` and `DIVISION_BY_ZERO`, the failing bound or divisor check is evaluated in the model before it is
shown. A model in which the check holds does not show the failure and is dropped. A model in which the check cannot be
evaluated, for example because an operand is read from the heap, is shown under `Counterexample (unchecked):`.

Enable model generation with `formver { counterexamples(true) }` in Gradle, or the compiler-plugin option
`counterexamples=true`. Generation is opt-in because measured model extraction adds substantial verification cost.
[`SiliconFrontend`](formver.compiler-plugin/viper/src/org/jetbrains/kotlin/formver/viper/SiliconFrontend.kt) controls
the backend configuration. Golden-file tests opt in with the test-only `COUNTEREXAMPLES` directive; ordinary goldens record
proof diagnostics independently of solver-selected values. The dedicated
[`counterexamples.kt`](formver.compiler-plugin/testData/diagnostics/language/diagnostics/counterexamples.kt) fixture constrains
its failing values uniquely.

## Integer arithmetic

`Int`, `Long`, `Short`, `Byte` and `Char` values have their Kotlin finite ranges. Arithmetic is checked in executable code and specification expressions alike, including contracts, laws, loop invariants and `@Pure` bodies. Each evaluated operation must have a representable result; overflow reports `INTEGER_OVERFLOW`. Byte and Short arithmetic uses Kotlin's resolved promotion to Int, while increment and decrement retain the receiver's width.

Division truncates toward zero; remainder has the dividend's sign. A zero divisor reports `DIVISION_BY_ZERO`. `MIN_VALUE / -1` overflows; `MIN_VALUE % -1` is zero. The `floorDiv` and `mod` extensions round toward negative infinity: `floorDiv` has the promoted width, `mod` has the divisor's width and sign, and both report `DIVISION_BY_ZERO` for a zero divisor. `floorDiv` overflows when its result leaves that width, as `Int.MIN_VALUE.floorDiv(-1)` does. Widening conversions are exact, and narrowing conversions must prove the target range. Char arithmetic and conversions must stay within 0 through 65535. Arithmetic failures are proof obligations, not catchable exits.

`@WrappingIntArithmetic` selects two's-complement wrapping for resolved primitive binary `+`, `-` and `*`, unary `-`, `++` and `--` with an `Int` result, so `-Int.MIN_VALUE` is `Int.MIN_VALUE`, and modular wrapping for binary `+`, `-` and `*`, `++` and `--` with a `UInt` result. `@WrappingLongArithmetic` selects them for the same operations with a `Long` or `ULong` result. `@WrappingNarrowing` selects two's-complement truncation for narrowing `toByte`, `toShort`, `toInt` and `toChar` conversions, so `200.toByte()` is `-56` and `(-1).toChar()` has code 65535; widening stays exact. Mixed Byte/Short/Int/Long operands follow Kotlin's overload resolution and exact widening. The annotations are independent and may coexist. They include compound assignments, the annotated function's body, its specifications and its `@Pure` body. In the body of a member function that overrides `Any.hashCode`, `Int` results of these operations wrap with no annotation, since `hashCode` may return any `Int`; so do the operations `@WrappingIntArithmetic` selects for `UInt`. This covers only that function's own body; inlined callees and lambdas keep their own mode. Other operations, including division/remainder and `floorDiv`, retain their checks. User-defined operators remain ordinary calls. Each converted function (including an inline callee) and separately converted lambda uses its own declaration's mode; specification DSL blocks use their containing declaration's mode. Callers evaluate callee contracts in the callee's mode and subsequent caller arithmetic in their own mode. The trust report lists these semantic opt-ins, and `hashCode` overrides that wrap, separately; they encode Kotlin semantics exactly and introduce no assumption.

### Unsigned integers

`UByte`, `UShort`, `UInt` and `ULong` values are the integers from 0 through 255, 65535, 2^32 - 1 and 2^64 - 1, with the
checks above: comparisons, literals such as `1u` and `0xFFuL`, `/` and `%` are exact, a zero divisor reports
`DIVISION_BY_ZERO`, and `+`, `-`, `*`, `++` and `--` report `INTEGER_OVERFLOW` when the result leaves the range, so
`0u - 1u` fails. `UByte` and `UShort` arithmetic is promoted to `UInt` as Kotlin resolves it. The wrapping
annotations select the result modulo 2^32 or 2^64 instead.

A conversion between a signed and an unsigned type, or between unsigned widths, such as `x.toUInt()`, `u.toInt()`
or `u.toUByte()`, is exact and must prove that the value is representable in the target type. `@WrappingNarrowing`
selects Kotlin's reinterpretation of the low bits instead, so `(-1).toUInt()` is `UInt.MAX_VALUE` and
`UInt.MAX_VALUE.toInt()` is `-1`. Other members of the unsigned types, such as the bitwise operations, shifts,
`floorDiv` and `mod`, have no model: a call is an opaque library call. Unsigned ranges are not converted.

```kotlin
fun midpoint(lo: UInt, hi: UInt): UInt {
    preconditions { lo <= hi }
    postconditions<UInt> { r -> lo <= r && r <= hi }
    return lo + (hi - lo) / 2u
}
```

### Bitwise operations

`and`, `or`, `xor`, `inv`, `shl`, `shr` and `ushr` on `Int` and `Long` have the JVM's two's-complement semantics in executable code and specifications. Shift distances are masked to their low 5 (`Int`) or 6 (`Long`) bits, so `x shl 33 == x shl 1` for an `Int`. `shl` wraps; it never reports `INTEGER_OVERFLOW`. None of these operations has a precondition.

The encoding stays in integer arithmetic, and its cost depends on the operands:

- A literal operand or shift distance becomes division and remainder by constant powers of two: `x and 0x7F` is `x mod 128`, `x shr 3` is floor division by 8. Literal masks and shifts add no case splits.
- Operands with no bit in common, as seen from masks and literal shifts in the same expression (`(b0 and 0xFF) or ((b1 and 0xFF) shl 8)`), combine as `+`. So do `a op b` when `0 <= a < 2^k` holds and the low `k` bits of `b` are clear, as for `b = x shl d` with a literal or variable `d`.
- `x and ((1 shl s) - 1)` written in one expression is the remainder of `x` by `2^s`.
- With an operand of 0 or -1, the result follows directly, as in ZigZag's `(n shl 1) xor (n shr 31)`.
- A variable shift distance multiplies or divides by a table of powers of two; a proof splits on the distance.
- Other combinations of two non-literal operands are summed bit by bit. Such proofs, and failing proofs about them, can reach the verification timeout. So can chains of literal `xor`s other than 0 and -1.

### Ranges and coercion

`x in a..b`, `x in a..<b` and `x in a until b`, and their `!in` forms, are comparisons with the ends when the
range is written at the membership test with `Int` or `Long` ends and the element is an `Int` or `Long`, in any
mix, as in `formatLength !in 0..Int.MAX_VALUE` for a `Long` `formatLength`. This includes `in` branches of `when`.
The ends are evaluated before the element, as Kotlin does. Membership in a range held in a variable or built
another way is a library call.

`coerceAtLeast`, `coerceAtMost` and the two-bound `coerceIn` on `Int` and `Long` have their stdlib definitions.
`coerceIn` throws when its minimum exceeds its maximum.

## Equality

Kotlin `a == b` is `a.equals(b)`, or `b === null` when `a` is null; `a != b` is its negation, and a `when` branch
compares its subject with `==`. SnaKt picks the meaning of `equals` from the static type of `a`:

- `Int`, `Long`, `Short`, `Byte`, the unsigned integer types, `Char`, `Boolean`, `String` and `Unit` compare by value.
- A class whose `equals` is `Any.equals` or `Enum.equals` for every value of the type compares by identity: a final
  class, an enum class, an `object`, and a sealed class whose inheritors all qualify.
- A source data class that does not override `equals` compares as its generated `equals`: `a === b`, or `b` is of
  the class and each primary-constructor property compares with `==`.
- A source class that declares `equals` calls it through its specification. The call may throw like any other.
- A type parameter compares by value or identity when one of its bounds does.
- Any other type, such as an interface, an open class, an `expect` class, an unbounded type parameter or a library
  class, gives an arbitrary result (Dynamic equality). The call may throw.

In a specification, an `equals` that is not a `@Pure` function, a data class with a `var` component and a data class
that contains itself compare through a relation that only says what a null `a`, a value type or an identity class
gives. `==` on an owned value whose `equals` is not value or identity equality is unsupported in executed code.

`@TrustedElementEquality` on a function makes an executed Dynamic comparison (`==`, `!=` or a `when`-subject
comparison) whose left operand's static type is a type parameter, nullable or non-null, use `EqualsRelation` as
its value. The exceptional exit remains possible when a catch is active. Value, identity, structural and declared
equality retain their meanings, as do Dynamic comparisons with other left-operand types. Each callee, including
an inline function, uses its own mode. The relation gains no reflexivity, symmetry or transitivity axioms.
Each annotated function is a trusted assumption in the trust report; residual trust is registered in
[Executed element equality](docs/soundness.md#executed-element-equality).

In specification blocks (`preconditions`, `postconditions`, `loopInvariants`, `verify` and laws), `a === b`
compares model references, and `a !== b` is its negation. Values injected one-to-one into `Ref` compare by value.
This introduces no assumption about `equals`.

In executed code, including `@Pure` bodies, `a === b` is rejected when both operands may hold a value of the same
type among `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean` and `String`, such as `x: Any` and `y: Int`. The JVM
boxes these values and shares boxes of small integers, so whether two boxes of one value are the same object
depends on the value and on where each box came from. External specifications' own type parameters denote model
elements and are exempt from this rejection.

The collection specifications compare elements with `==`: `list.contains(x)` holds when `x == list[i]` for some
index, and `Set.contains`, `MutableSet.add` and `Map.containsKey` look for an element equal to the argument. An
element held by the collection itself is contained. These specifications assume that element `equals` is an
equivalence relation consistent with `hashCode` that does not change while the element is in the collection
(docs/soundness.md, "Element equality in collections").

## Types

A value has the facts of its Kotlin type: its class and whether it can be null. These types have no class of
their own:

- A type parameter has its upper bounds: inside `fun <T : Base> f(x: T)`, `x` is a `Base`. A cast `x as T` or
  `x as? T` gives only the erased bound that the JVM checks: with `T : A, T : B` for interfaces `A` and `B`, the
  result is an `A`.

- `T & Any` is `T` without null.
- An intersection type, such as the `A & B` that a value of type `A` gets when smart cast to `B`, has every
  component type.
- An anonymous object type has its declared supertypes.
- A captured type, such as the type of a read through a star projection, has its upper bounds: reading `value`
  from `Box<*>`, where `Box<T : Number>`, gives a `Number`.
- A Java platform type is its upper bound, which admits null: `String!` is `String?`, and the platform collection
  type `(Mutable)List<String!>!` is `List<String!>?`.

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

A secondary constructor may start with a `preconditions` block over its parameters. It cannot read `this`,
including the properties of the object under construction. The body assumes these conditions; constructor calls
and `this(...)` or `super(...)` delegations establish them before construction executes.
Constructor postconditions and specification blocks in `init` blocks have no contract semantics.

Multiple conditions are implicitly conjoined. The postconditions block receives the return value as its parameter.
A call on an instantiated generic receiver uses the member declaration's specification, with the class's type
parameters standing for the call's type arguments.

A condition is a single expression. Besides operators, it may use `if`, `when`, `?.`, `?:` and `as?` for their
values, and call `@Pure` functions and inline functions whose bodies are in the sources being compiled, together
with the lambdas passed to them. Inside such a lambda, a local can be declared and initialized once, and a
`return@label` can end the lambda as its last expression. Anything that needs a statement is reported as an
unsupported construct: loops, assignments to other variables, an `if` or `when` whose value is unused, and calls
to other functions. Supported standard-library scope functions are inlined as described under
Inline functions and default arguments.

### Context parameters

A context parameter (`context(c: C) fun f()`) is an ordinary parameter of its function. Specifications and
contracts may mention it like any other parameter, and it is shared unless annotated `@Unique` or `@Borrowed`.
A call passes the context arguments Kotlin resolves for it, including a caller's own context parameter or the
receiver of an enclosing `with(x) { }` or single-value `context(x) { }`. In the generated Viper method, the receivers come
first, then the context parameters, then the value parameters. Lambdas with context parameters follow the same
rule. A property with context parameters is reported as an unsupported construct.

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
top-level `@ExternalFunction` or `@ExternalProperty` model. Its value parameter types, ownership annotations included, and positional
type parameters match the target; this picks the overload it specifies. For a property model it specifies the getter
and has no value parameters. It returns `Unit`. An extension receiver
is allowed for an external function or property model and matches the target's receiver type. Its body holds only a
`preconditions` block, a `postconditions` block, whose type is the target's return type, and a `throwsOnlyIf` block,
in this order; a property or `@Pure` target takes no `throwsOnlyIf` block. Its parameters stand for the member's parameters
by position. It is never converted, verified or called. A member has at most one `@SpecOf` function, none if its
body has a specification. Each violation, and a call to a `@SpecOf` function, is
reported as `INVALID_SPEC_OF`.

### External specifications

Use external specifications to describe declarations whose implementations are in compiled dependencies. Supply
ordinary Kotlin files as external specification inputs. `@ExternalSpec("package.Type")` on a model interface binds
its members to that dependency type. `@ExternalMember("name")` selects a different target member name.
`@ExternalFunction("package.function")` binds a top-level model function, including an extension function.
`@ExternalProperty("package.property")` binds a top-level model property, including an extension property.
`@ExternalConstructor("package.Type")` binds a top-level, non-extension model function to one public constructor.
Its type parameters and bounds match the dependency class's in order. Its non-null result is that class's bound
model applied to the function's own type parameters in order, without projections or additional nullability.
Its value parameters match the constructor by count, type and vararg status. The result's `@Unique` annotation is
the model's ownership claim; a result without it is shared. Its contracts use the function-model machinery below.
A library constructor without such a binding, of a bound class or one with a bound supertype (such as
`LinkedHashMap()`), gives a shared object: nothing establishes the model's laws and observers for it, so calls on it
are unchecked library calls. The [primitive arrays](#primitive-arrays), such as `UIntArray`, which implements
`Collection`, are exempt: their constructors give unique arrays.
A library type that binds an observer of a bound supertype through its own members, such as `java.util.SequencedMap`,
has every observer of that supertype's model.

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
from their leading specification blocks or a sibling `@SpecOf` holder. Property-model getter bodies are placeholders
too; a getter's contract comes from a `@SpecOf` holder. Model declarations are available during
analysis and removed before code generation. Application code references dependency declarations; references to
model declarations are compilation errors.

A member whose model owns its dispatch receiver (`@Unique`, or `@get:Unique` for a property) accepts any receiver,
and so does an extension whose model's extension receiver is `@Unique`. A
call whose receiver is unique just before it gets the model's contract, observers and laws. A call on any other
receiver is the opaque library call: it may throw, and its result is known only through its type, except that the
`size` of a Collection or a Map is at least 0. Such a call is
reported as `UNCHECKED_LIBRARY_CALL`, and a specification cannot contain one. A parameter declared
`@Unique @Borrowed` gives the function's calls the model's contract. A member declared in source that owns its receiver
accepts only a unique one.

Bindings match callable kind, parameter and result types, generic parameters and bounds, extension receivers,
varargs, property mutability and suspend status. A `val` model may also bind a `var` with a private setter; only
its getter is bound. Model-interface types stand for their bound dependency types.
A model interface declares only functions and properties, and `@ExternalMember` appears only inside one.
Unresolved, incompatible, ambiguous and duplicate bindings are errors, each reported as `INVALID_EXTERNAL_SPEC` on
the model declaration; a module with any of them is not verified. Bundled and user specifications share this check.

A class declared in source with a modelled supertype, such as a class implementing `MutableMap`, is not modelled.
Its constructors give shared objects, as for a library class without a binding, so no `@Unique` local, parameter,
property or result can hold one, and calls of modelled members on one are unchecked library calls. Its own members are
converted with their own contracts. Its overrides must repeat the model's ownership of value parameters, extension
receiver and result, but not of the dispatch receiver. An override or inherited implementation of a model observer
need not be `@Pure`, and the class may implement a model observer by delegation.

External contracts, `throwsOnlyIf` conditions, purity, ownership and laws are trusted assumptions about the dependency. SnaKt verifies
callers against them without proving the dependency's implementation. A call of a `@Pure` model has no exceptional
exit, so a `@Pure` binding asserts that the library function does not throw wherever the model's precondition holds;
bind a function that may throw on other inputs without `@Pure`, or with a precondition that excludes them. Audit each model against the exact
library version in the compilation classpath; a matching signature alone establishes no behavioral compatibility.
A binding to a declaration that SnaKt verified in another module is reported as the warning
`EXTERNAL_SPEC_OF_VERIFIED_DECLARATION` on the model: the model's contract replaces the one SnaKt proved. SnaKt marks
each declaration it verifies with `@Verified` in the compiled output and reads that mark from the dependency. Only JVM
dependencies, whose class files keep the mark, are tested; a dependency that does not keep it gets no warning.
A contradictory specification would let every proof that assumes it succeed, so each compilation checks the
specifications it loads from `externalSpec` files for contradictions: the precondition of each model with a
specification, its postcondition given the precondition, and the laws each model interface declares together with
those it inherits. Each contradiction is the error `EXTERNAL_SPEC_VACUOUS` on its model. docs/soundness.md, "External
specifications", says which contradictions the check misses. The check goes through the verification cache. The bundled specifications are checked by the repository's tests instead of in each compilation.
The trust report lists the bindings and `throwsOnlyIf` conditions each verified declaration relies on (docs/soundness.md, "The trust report"). The bundled assumptions are the
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
        return 2
    }
}
```

A call or property access through `super`, as in `super.f()`, `super<I>.f()` or `super.p`, runs the named
supertype member on `this`, whatever overrides it. The call is verified against that member's contract; a member
without one gives the caller only its result type. `super` is an unsupported construct where `this` is under
construction, in a constructor or initializer, where `this` is `@Unique`, and in a lambda that is not inlined.

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
    @Pure override val size: Int get() = 1
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

### Local functions and classes

A local function, and each member and constructor of a local class, is verified
as a declaration of its own. A parameter or `val` of an enclosing function that
it captures is known inside by its type and by the facts that hold where the
local function or class is declared: the enclosing preconditions and the path
conditions there whose free variables are captured `val`s or parameters and
that use only operators, built-in functions and type tests of types that the
local declaration's own program knows; facts that read fields or call other
functions are dropped. Every call runs
after the declaration and a
`val` does not change, so these facts still hold. When the declaration is
reached on several paths, the facts of one of them hold. These facts are
collected only when the declaration is in the body of a named function without
specified lambdas; elsewhere, such as in a lambda, accessor, constructor or
`init` block, a captured value is known by its type only.

A captured `var` is treated as for a lambda value ([Lambda values](#lambda-values)):
in the enclosing function, once the declaration has been reached, every read
yields an arbitrary value of its type; before that, the `var` keeps its tracked
value. Inside the local function or class every read yields an arbitrary value
and writes are not kept.

A receiver of an enclosing declaration that a local function or class reads is
an arbitrary shared value of its type, the same at each read; no facts about it
carry over from the declaration.

```kotlin
fun scale(n: Int): Int {
    preconditions { n > 0 }
    var calls = 0
    fun step(): Int {
        verify(n > 0)             // verifies: a precondition over a captured parameter
        calls = 1                 // not kept
        return n
    }
    val r = step()
    verify(calls == 1)            // fails: calls is arbitrary once step is declared
    return r
}
```

A contract of a local function or member that reads a captured value, a
`@Pure` local function or member that reads one, and a local function or class
that shares its name with another declaration in one program are reported as
`UNSUPPORTED_FEATURE`.

### Inner classes and anonymous objects

In a member or constructor of an inner class or anonymous object, the instance
of an enclosing class, read as `this@Outer` or as the implicit receiver of a
call or property read, is an arbitrary shared value of that class, the same at
each read in one body; no facts about it carry over from the object's creation.
A contract or `@Pure` member that reads an enclosing instance is reported as
`UNSUPPORTED_FEATURE`. Inside a local class, these receivers follow
[Local functions and classes](#local-functions-and-classes).

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
Delegation to a library superclass constructor is not executed: the fields of that superclass and its ancestors,
except `lateinit` fields, are assumed to hold values of their declared types.
The returned representation and its opened-predicate description include the invariants. A violating proof reports
`VERIFICATION_CONDITION_NOT_ESTABLISHED` naming the law. Delegated properties in law-bearing classes are rejected with `INVALID_LAW`.

A construction-discipline violation is reported with its reason: `INVALID_LAW` for classes with own or inherited
class laws, and `VERIFICATION_SKIPPED` for other classes. Callers of such a constructor still assume the class laws.

Unfolding an owned class receiver exposes its invariant to the member body. Every refold proves the invariant,
including refolds for calls and control-flow joins. On member exit, a retained unique receiver with class laws is
refolded even when the receiver is consumed rather than borrowed. Ownership transfers prove the invariant before
moving the representation. An invariant violation reports `VERIFICATION_CONDITION_NOT_ESTABLISHED` naming the law.

For an owned receiver, a concrete observer's function definition reads its pure body under an unfolding of the
class's unique predicate. The interface-law harness connects the invariant's expanded field expression to the
concrete observer through that definition. It proves inherited interface laws from the class invariant without
an additional observer axiom.

## Loop Invariants

```kotlin
fun sumUpTo(n: Int): Int {
    preconditions { 0 <= n && n <= 1000 }
    var sum = 0
    var i = 0
    while (i <= n) {
        loopInvariants {
            0 <= i && i <= n + 1
            0 <= sum && sum <= i * 1000
            sum + sum == i * (i - 1)
        }
        sum += i
        i++
    }
    return sum
}
```

Arithmetic in invariants is checked like any other (see Integer arithmetic), so the invariants bound `i` and `sum`
to show that `sum += i`, `i++` and `i * (i - 1)` stay within `Int`.

The rules are as follows:
- Loop invariant must hold when the loop is entered.
- The loop body may assume the condition holds.
- Loop invariant must hold after each iteration.
- Loop invariant must hold when the loop is exited by `break`, and when it is exited because the condition fails.
  When the condition has side effects, as in `while (i++ < n)`, the invariant is checked where the condition is
  evaluated rather than where it fails: code after such a loop knows that the invariant held before the last
  evaluation of the condition.
- Code after the loop may assume the condition fails.
- Of the properties of unique objects the loop reads or writes, including through functions and lambdas inlined into
  it, code after the loop knows only what the invariant says. What was known about the other unique objects still
  holds.

A `for` loop over an `Int` progression written with `until`, `..<`, `..` or `downTo`, optionally followed by
`step`, or over `indices` of a `String`, an `Array<T>` or a [modelled primitive array](#primitive-arrays), which is `0 until` its length or size,
puts its `loopInvariants` at the start of its body:

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

A `do`-`while` loop runs its body once, then again while its condition holds; `continue` goes to the condition,
which may read the body's local variables. Its `loopInvariants` lead the body. They must hold when the loop is
entered, after every run, the last included, and when the loop is exited by `break`. Code after a loop that its
condition ends may assume the condition fails.

```kotlin
fun countTo(n: Int): Int {
    preconditions { 1 <= n && n <= 100 }
    var i = 0
    do {
        loopInvariants { 0 <= i && i <= n }
        if (i < n) i++
    } while (i < n)
    verify(i == n)
    return i
}
```

A `loopInvariants` call that does not lead the body of a `while` or `do`-`while` loop, of a `for` loop described here
or of a lambda passed to a collection template (Collection iteration) is an `UNSUPPORTED_FEATURE`, as in `for (c in s)`
over a String.

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

A derived trigger fires only where a caller names the current term. A caller
that makes two calls in a row, or compares the entry value with a direct read of
the storage, names only the entry value, so a frame over a `@Pure` observer that
such callers use lists both terms: `triggers(elementAt(i), old(elementAt(i)))`.
`verification/user_invariants/observer_frame_triggers.kt` shows the difference.
Triggers matter only where a quantifier is assumed; on a quantifier that is being
proved, in a `verify` or in the function's own postcondition, they have no effect.
A trigger never changes what a quantifier states: a poor choice makes a proof fail
or time out, and cannot make a false claim verify.

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
immutable and need no annotations. A `var` property, or a primitive array or `Array<T>` element,
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
call to a function returning `@Unique`, until it is used as a shared value (see
[Inferred locals](#inferred-locals)). A constructor call does not count, so
a fresh object that should be owned needs the annotation:

```kotlin
val a = make(1)                   // unique
val b = Node(2, null)             // shared: writes to b.next are dropped, with a warning
val c: @Unique Node = Node(3, null)  // unique
```

A property declared without a type at the top level or in an `object` is
shared, whatever its initializer, since any function can reach it. `@Unique`
written on its type makes it owned, and its moves are checked as for any
unique path.

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

`@Borrowed` on a parameter of a function type, as in
`var onClose: ((@Borrowed Event) -> Unit)?`, admits only functions that do not
store, return or capture that argument. `null`, and any expression of type
`Nothing` such as a `throw`, holds no function and is accepted wherever a
function type is expected, whatever its annotations.

### Unique and shared paths

A unique path starts at a local, a parameter or an owned receiver. Outside
construction, a property of a shared `this` or of a call result, and a
top-level property, is shared, even when it is `@Unique` or takes uniqueness
from its initializer: a member call on it whose model owns its receiver is the
opaque library call, and its `var` properties and array elements are shared.

Through a unique path, `var` reads and writes are real, and the verifier
tracks their values. Through a shared path, every `var` read yields an
arbitrary value. Shared `var` writes follow the law-bound field rule in Class laws;
other shared `var` writes are dropped from the specification, because other code
may hold the same object.

```kotlin
class Counter(var n: Int)

fun bump(c: @Unique @Borrowed Counter) {
    preconditions { c.n < Int.MAX_VALUE }
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

A conditional, `when` or block moves a fresh unique value when every branch
yields one or leaves abnormally (`throw`, `return`, `break` or `continue`).
A call returning `Nothing` as an arm of an owned conditional move is rejected,
including calls with an `@Unique` result annotation.

### Inferred locals

A local declared without a type takes its uniqueness from its initializer, as
described in [Annotations](#annotations). Its author did not ask for that
uniqueness, so where the local is used as a shared value SnaKt weakens it
instead of reporting an error:

- Passing the local itself to a parameter or receiver that is neither
  `@Unique` nor `@Borrowed`, or storing it in a property or local that is
  neither `@Unique` nor `@Borrowed`, makes it shared from that point on
  instead of moving it. The callee or the object may keep it. A local
  declared without a type that receives it, as in `val d = c`, is shared
  too. Passing or storing a property of the local, such as `c.next`, still
  moves that property.
- Assigning the local a shared value makes it shared from that point on.
  Assigning it a borrowed value makes it borrowed, and the rules for
  `@Borrowed` values apply.

Where control flow joins, the local is shared if it is shared on any incoming
path. Through a shared local every `var` read yields an arbitrary value, as
through any shared path. A shared local is still rejected where a declaration
requires a unique value: as the argument of a `@Unique` parameter, as the
result of a function returning `@Unique`, or as the value of a `@Unique` local
or property. A call on it to a library member whose model owns its receiver is
the opaque library call (see [External specifications](#external-specifications)).
A local whose type is written, such as `val c: @Unique Counter`,
follows the rules for moves above.

```kotlin
fun keep(c: Counter) { }

fun f(): Int {
    val c = make()                // unique
    c.n = 1
    keep(c)                       // c is shared from here on
    return c.n                    // accepted; the value is unknown
}

fun g() {
    val c = make()
    keep(c)
    consume(c)                    // error: consume requires a unique value
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

A function compiled without its source, such as a library function, keeps no
default values. An argument omitted in a call of one is some value of the
parameter's type: the callee's precondition must hold for every such value, and
the caller learns its postcondition for an unknown one.

### Vararg parameters

A vararg parameter is an array: `vararg xs: Int` is an `IntArray`, and `vararg xs: T` is an `Array<out T>`. The
callee holds it shared, so its elements are unknown there. Each call passes a fresh array holding its arguments in
order, or an empty array when they are omitted. A spread `*a` contributes a copy of the elements of `a`: it reads
`a`, which stays owned when it was. A single element argument is shared, so passing a unique path as one moves it.

For a [primitive array](#primitive-arrays) with an element model and for `Array<T>`, the passed array's size is the
number of elements, each single argument is the element at its index, and the elements copied from an owned array
equal its elements. The elements copied from a shared array are unknown. For other array classes only the arguments'
evaluation is modelled.

```kotlin
fun count(vararg xs: Int): Int {
    postconditions<Int> { it == xs.size }
    return xs.size
}

fun caller(a: @Unique IntArray) {
    preconditions { a.size == 2 }
    val n = count(1, *a, 2)
    verify(n == 4)                // verifies; `a` is still owned
}
```

### Lambda values

A lambda that is not invoked in place, such as one stored in a local, passed to
a function that is not inline or returned, evaluates to a fresh non-null value
of its function type; nothing else is known about it. Calling a function-typed
value is a call of a function object, which has no contract: its result is an
arbitrary value of the result type.

The holder of a lambda value may run it later, any number of times. From the
lambda's creation on, each local `var` it captures is read as an arbitrary value
of its type, and each object it captures is shared. Before the creation, the
`var` keeps its tracked value.

```kotlin
fun counter() {
    var x = 0
    x = 1
    val before = x
    verify(before == 1)           // verifies
    later { x = 2 }
    val after = x
    verify(after == 1)            // fails: the lambda may have run
}
```

The body of a lambda value written in a function body is verified as a function
of its own, against no contract. One written in a property initializer or an
`init` block is not verified. Its parameters are arbitrary values of their types. Each `val`,
parameter or receiver it captures is an arbitrary shared value of its type, the
same at each read. Each local `var` it captures is an arbitrary value at each
read, and the body's writes to it are not kept. Such a write is still a side
effect: a `verify` or `refute` condition containing one is a purity violation. A verification failure in the
body is reported in the body; it does not make the function that creates the
lambda fail.

```kotlin
fun body() {
    var x = 0
    later {
        x = 1
        val r = x
        verify(r == 1)            // fails: another read gives another value
    }
}
```

A lambda value may capture a local the uniqueness checker inferred unique; the
local is shared from the capture on, so passing it to a `@Unique` parameter
afterwards is a `UNIQUENESS_MISMATCH`. Capturing a root whose type is annotated
`@Unique` or `@Borrowed` is rejected (see [Rejected constructs](#rejected-constructs)).
A lambda that is inlined, has a `callsInPlace` contract, is an argument of a
specification or of an array constructor, is SAM-converted or is lifted to a
class does not share the local `var`s it captures. If the conversion has to keep
such a lambda as a value, such as one passed to a non-inline function with a
`callsInPlace` contract, it is an `UNSUPPORTED_FEATURE` when it captures a local
`var`.

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
its observer and the templates below on it. For a `Set`, the sequence is
duplicate-free and `contains` holds for each of its elements.
`iterationSequence(map)` gives entries aligned with a duplicate-free keys
sequence each of whose keys satisfies `containsKey`;
entry values agree with lookup at those keys. Set and Map iteration order has
no guarantee. `Map.Entry.key` and `value` are immutable observations that
require no ownership of the entry.

The inline collection templates `forEach`, `forEachIndexed`, `map`, `filter`,
`any`, `all`, `none`, `count`, `indexOfFirst`, `firstOrNull`, and `fold` use this observer
for `Collection` receivers. `find`, which the library documents as `firstOrNull` with a predicate, uses the
`firstOrNull` template. The supported Map overloads are `Map.forEach` and
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

An ordinary `for` loop over a collection calls `iterator()`, `hasNext()` and
`next()`. Over an owned `List`, the iterator follows the `List.iterator()` model.
Over a `List` that is not owned, the iterator is owned and its elements are
unconstrained values of the element type. Over other collections these calls
are opaque library calls. Such a loop takes no `loopInvariants`; the templates
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
platform-typed value at a use listed under [Verification Control](#verification-control) leave abnormally.
Their operands are evaluated first.
Casts and `!!` in specifications and `@Pure` bodies follow
[Pure functions and specifications](#pure-functions-and-specifications).

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

Primitive array, `Array<T>` and String index bounds, division by zero, and integer overflow
remain verification obligations inside `try`. An element access of an unmodelled
primitive array type, such as `ShortArray`, is an ordinary library call, which may throw. The checked arithmetic
fragment is described under Integer arithmetic. Verification does not establish freedom
from exceptions in opaque library operations or from JVM resource, linkage,
or asynchronous failures.

#### Conditions for throwing

A `throwsOnlyIf` block after a function's `preconditions` and `postconditions` blocks states when an exception may
leave the function:

```kotlin
fun digit(d: Int): Int {
    postconditions<Int> { !(d < 0 || d > 9) }
    throwsOnlyIf { d < 0 || d > 9 }
    if (d < 0 || d > 9) throw IllegalArgumentException()
    return d
}
```

The condition follows the rules for preconditions and is read in the function's entry state: it reads the parameters
and the heap as they were on entry, and `old(e)` there means `e`. It says nothing about the state after the throw.
Together with the postcondition `!c`, `throwsOnlyIf { c }` states that the function throws exactly when `c` holds.

At each point where an exception may leave the function, the condition must hold, or
`THROWS_CONDITION_NOT_ESTABLISHED` is reported there. These points are the abnormal exits listed above and, in a
function with the condition, the exceptional exit of every call that is not `@Pure`, including a constructor call such
as `IllegalArgumentException()`. On a call's exceptional exit, a callee with its own `throwsOnlyIf` condition gives the
assumption that its condition held for the call's arguments when the call started; a callee without one, a function
object, and an opaque library call give no assumption. Catch selection keeps its over-approximation: an exception
thrown inside a `try` may escape the function even when a `catch` would receive it, so the condition must hold at
that point too. A call resolves to the condition of the declaration it names. A declaration in a dependency carries
none, so calls into it give no assumption, unless an external specification binds it: a function model's
`throwsOnlyIf` block, in its own body or in its `@SpecOf` holder, is the library function's condition. That condition
is a trusted assumption, and the trust report lists it.

An override without a specification of its own inherits the overridden declaration's, including its condition. An
override with a specification throws only where the overridden condition permits: its own condition must imply the
overridden one, and an override with a specification but no condition must establish the overridden condition
wherever it may throw. A failure is reported as `OVERRIDE_NOT_REFINING`.

A function without `throwsOnlyIf` is unchanged: nothing is checked where an exception leaves it, unless
[Exception freedom](#exception-freedom) is selected. `throwsOnlyIf` is an
unsupported construct on a `@Pure` function and on a constructor. JVM resource, linkage and asynchronous failures,
such as `StackOverflowError` and `OutOfMemoryError`, may leave any function whatever its condition.

#### Exception freedom

The `exception_free_by_default` option ([configuration](docs/configuration.md#exception-freedom)) treats every
function of the compiled sources that has no `throwsOnlyIf` condition, its own or inherited, as if it had
`throwsOnlyIf { false }`: no exception may leave it, and its callers assume that none does. The rules of
[Conditions for throwing](#conditions-for-throwing) apply unchanged, except where this section says otherwise. A
function with its own condition keeps it and reports `THROWS_CONDITION_NOT_ESTABLISHED`.

Where an exception may leave an exception-free function, the error names what raises it:

| Error | Raised by |
|:--|:--|
| `EXCEPTION_FROM_THROW` | `throw`, including the construction of the thrown value, and the other abnormal exits listed under [Exceptions](#exceptions), such as failed `require`, `!!` and casts. |
| `EXCEPTION_FROM_UNSPECIFIED_CALL` | A call whose callee has no `throwsOnlyIf` condition: an opaque library call, a declaration of a dependency, a custom property accessor, or a library operation such as an unmodelled `equals` or `toString`. |
| `EXCEPTION_FROM_THROWING_CALL` | A call whose callee's `throwsOnlyIf` condition may hold for its arguments. |
| `EXCEPTION_FROM_FUNCTION_OBJECT` | An invocation of a function object. |
| `EXCEPTION_IN_TRY` | Any of the above inside a `try` with a `catch`. A `catch` may receive the exception, but catch selection is over-approximated, so the exception may also escape. |

The exit after a `finally` block that rethrows the exception of its `try` is not reported again: the exit that
raised the exception reports it.

The option covers declarations as follows:
- A `@Pure` function is unaffected: calls of pure functions have no exceptional exit, and its body proves its
  operations do not fail.
- A constructor is exception-free when its class's construction is verified (see [Verification Control](#verification-control)): its
  construction, including property initializers, `init` blocks and delegation to source constructors, is checked,
  and its callers assume that the constructor does not throw. Any other constructor gives no assumption.
- An override of a library declaration is exception-free and is checked like any other function. A call that
  resolves to the library declaration gives no assumption, so it reports `EXCEPTION_FROM_UNSPECIFIED_CALL`.
- An override with its own condition of an exception-free source function must refine it, so
  `OVERRIDE_NOT_REFINING` is reported unless the override's condition is unsatisfiable.
- An external specification model keeps its own condition, if any; the library function it binds is not compiled
  source. A `@Pure` model gives its calls no exceptional exit.
- An `inline` function is verified where it is inlined (see [Inline functions](#inline-functions-and-default-arguments)),
  against the condition of the function it is inlined into.
- A custom property accessor and a lambda converted as a program of its own are not checked, and a call of a custom
  accessor gives no assumption.

A function whose verification is skipped keeps its condition, so its callers still assume it throws nothing, as they
assume its other contract clauses.

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

### Primitive arrays

`IntArray`, `ByteArray`, `CharArray`, `LongArray`, `UByteArray`, `UShortArray`, `UIntArray` and `ULongArray` hold
their elements as a sequence of values of their element type. Each element
stays within its type's range, so `bytes[i].toInt()` lies in -128 through 127:
widening an element is exact, which for `Byte` is sign extension. Other
primitive array types have no element model. The rules below are stated for
`IntArray` and apply to each modelled class.

- `arr.size` needs no ownership and works on any array. It is never negative.
- `arr[i]`, `arr[i] = v`, `arr.get(i)`, `arr.set(i, v)`, `arr[i] += v` and
  `arr[i]++` read and write the elements of a unique array.
  None of them moves the array.
- On a shared array, an element read yields an arbitrary value of the element
  type and an element write is dropped. All element accesses check the index bounds, including
  discarded reads. In a specification, an element read needs a unique array.
- `IntArray(n)` creates an array of `n` zeros; `CharArray(n)` holds `n` copies of
  the character with code zero. As with any constructor call, a
  local holding it owns it only when declared `@Unique IntArray`.
- `IntArray(n) { init }` runs `init` on each index from `0` to `n - 1` in
  order, inlined into a loop. When the body of `init` is a single pure
  expression, every element is known to equal it at its index afterwards;
  otherwise only the size is known. The loop keeps ownership of the unique
  data around it, and keeps facts about that data as any loop does (see Loop
  Invariants).
- `UIntArray(n) { init }` and its unsigned siblings are library functions, not constructors; a call is an opaque
  library call, and its lambda is unsupported.

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

`@PointwiseArrayWrites` on a function changes how the element writes in its body and its lambdas, on primitive and
generic arrays and in `IntArray(n) { ... }`, reach the verifier, not what they mean. Without it a write replaces the
contents by the sequence update. With it the contents become a fresh sequence that has the old length, holds the
written value at the index and equals the old contents at every other index. The two encodings describe the same
sequence. The pointwise one keeps the verifier's sequence update and slicing axioms out of the proof of single-element
writes, which can make a function with many writes and quantified invariants verify where the update encoding times
out. `fill` and `copyInto` on `Array<T>` keep their slicing encoding under the annotation. It also gives no
multiset fact about the write, so a statement about `contents(arr)` across a write needs the update
encoding (see Multisets).

### `Array<T>`

A unique generic array stores a sequence of references. Indexed reads and writes,
including `get` and `set`, follow the ownership and bounds rules of [primitive arrays](#primitive-arrays).
A read inhales its static element type; the unchecked-cast assumption is in
`docs/soundness.md`, "Type invariants". Elements themselves are shared values.
Storing an owned reference moves that value, including through an erased cast.
Indexed assignment and `set` borrow the array receiver.
`size` is permission-free and `lastIndex` is `size - 1`.

`arrayOfNulls<T>(n)` requires `n >= 0` and creates `n` null slots.
`emptyArray<T>()` creates zero slots. A local owns either result when declared
`@Unique Array<...>`. `Array(n) { ... }` and `arrayOf(...)` have no element model.

`src.copyInto(dst, destinationOffset, startIndex, endIndex)` requires distinct
unique paths for `src` and `dst`. It replaces `endIndex - startIndex` destination
slots beginning at `destinationOffset` with the source range and preserves all
other slots. Both arrays are borrowed. The result aliases `dst`, so assigning
it moves the destination path. The same path in both roles is `UNSUPPORTED_FEATURE`.

`arr.fill(element, fromIndex, toIndex)` requires a unique receiver. It replaces
exactly that range with `element` and moves an owned element argument. Shared
bulk receivers or destinations are `UNSUPPORTED_FEATURE`.

Bulk ranges are half-open. Each call proves `0 <= from <= to <= size`; copying
also proves `0 <= destinationOffset <= dst.size` and that the copied range fits.
Omitted starts and offsets are zero, and omitted ends are the receiver size.
Explicit arguments are evaluated once in source order.

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

In specifications, laws and `@Pure` bodies, `x as T` requires proof of its runtime
type check, and `x!!` requires proof that `x` is non-null. Failures report
`CAST_NOT_ESTABLISHED` and `NULL_ASSERTION_NOT_ESTABLISHED`. An earlier guard may
establish these obligations. `as?` returns the operand on a successful runtime
check and null otherwise. These operations supply no permissions or generic-argument
facts; type-parameter casts check the erased bound. Reference-array casts requiring runtime
component checks, including `as?`, are unsupported; a compiler-proven redundant
component check is permitted. Primitive-array casts use their nominal runtime type.

A read through a cast, smart cast, `!!` or safe-call subject uses the source
owned path's presentation: its root's declared type, or its last owned field's
declared type. Only mutable fields on that presentation's base-class chain are
held. For `b: @Unique Base`, `(b as? Sub)?.count` can read `Base.count`, while
`(b as Sub).extra` cannot read a mutable field declared only by `Sub`. Thus this
foundation does not support subtype-only mutable-field reads. An interface
presentation supplies no concrete class ownership after a downcast. Shared sources
remain shared, and the pure result restrictions below apply through these views.

### Read-only view locals

In specifications, laws and `@Pure` bodies, a reference-valued local `val` is a
view when its initializer is a parameter, receiver or `this`, followed by final,
non-overriding backing `val` fields, optionally refined by `as`, `as?`, `!!` or
smart casts. Views of views compose. The initializer is evaluated once; every
ownership check and read uses the source path and its presentation. A view
creates no owned root, permission or invariant assumption. A nullable view has
no ownership on either branch.

Owned-source views may be observed by field reads, type and equality tests,
passed to non-inline `@Pure` calls or specification builtins, and used to
initialize another view. Duplicate and overlapping unique arguments are checked
against the source paths. Returning, forwarding through a non-view initializer,
assigning, storing or capturing an owned-source view is rejected with
`UNSUPPORTED_OWNERSHIP` (`BORROWED_VIEW_ESCAPE`). Shared-source flows retain the
ordinary shared-value rules.

An owned-source view cannot cross an inline actual-to-formal value, receiver or
context binding, including template and annotated inline callees. Lexical
reads inside an inline lambda are supported only when every expanded use obeys
the view rules; returning, forwarding or capturing the view is rejected. There
is no inline view propagation or general borrowing across writes or moves.

A stable view in `old(...)` denotes its evaluated object and requires the
source's old-state permissions. Specification clauses may declare views;
quantifier and trigger blocks reject local declarations with
`UNSUPPORTED_FEATURE`. Uncast aliases outside the stable view grammar retain
the limitation recorded in [Soundness](docs/soundness.md#non-view-owned-aliases).
Cast-bearing non-view aliases and refinements of their local lineage are rejected.

A specification may index a unique array with a `@Pure` call that borrows the
array's owner, as in `elementData[positiveMod(head + i)]`, also inside `forAll`.
The call's preconditions must hold in the state the specification reads: in a
postcondition that is the state after the call, so a precondition that reads a
`var` property needs the property framed by an earlier clause, such as
`elementData.size == old(elementData.size)`. Inside `old(...)` the call reads
the state before the call.

```kotlin
class Ring(var elementData: @Unique Array<Any?>) {
    @Pure @Unique @Borrowed fun positiveMod(i: Int): Int {
        preconditions { i >= 0 && i - elementData.size < elementData.size }
        return if (i >= elementData.size) i - elementData.size else i
    }

    @Unique @Borrowed fun get(index: Int): Any? {
        preconditions { index >= 0 && index < elementData.size }
        postconditions<Any?> { result ->
            elementData.size == old(elementData.size)
            result === elementData[positiveMod(index)]
        }
        return elementData[positiveMod(index)]
    }
}
```

A `@Pure` function cannot return an alias into data its caller still owns. Its
result cannot be `@Unique`. A function with a `@Unique` parameter must return a
value type, with an exception for direct element reads of a unique `Array<T>`,
optionally wrapped in an `as` cast that needs no runtime check: the cast's erased
target is a supertype of the element type, as in `a[i] as T` for a type parameter
`T`. A checked cast such as `a[i] as Payload` is rejected even when a precondition
establishes it.
Array elements are shared values (see [Array<T>](#arrayt)); returning one transfers
no ownership. Pure observers may also return shared observations. Returning an
owned path from a unique receiver or parameter reports `PURITY_VIOLATION` during
conversion. Pure element reads prove bounds and result typing without inhaling
result invariants.

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
mention that application itself. The
[`unique_tree_recursion.kt`](formver.compiler-plugin/testData/diagnostics/language/pure_functions/unique_tree_recursion.kt)
fixture demonstrates this in `rotateLeft`: assertions expose `cappedSize(right)`
before the move and `cappedSize(t)` after it. The size observer saturates at a cap
so its arithmetic remains within `Int`. An unrestricted recursive node count
can overflow even when its recursion terminates; the same fixture includes that
negative case.

### Termination of pure functions

A recursive `@Pure` function must declare a termination measure with
`decreases(...)` in its `preconditions` block. Without one, a definition such
as `loopy(x) == loopy(x) + 1` would let callers prove `false`. A function is
recursive when it can reach itself through calls in its specification or body,
directly, through other functions, or through the laws of a class whose
predicate it unfolds. Non-recursive functions need no measure.

```kotlin
@Pure
fun smallAckermann(m: Int, n: Int): Int {
    preconditions {
        0 <= m && m <= 1
        0 <= n && n <= 2147483646 - m
        decreases(m, n)
    }
    postconditions<Int> { r -> r == m + n + 1 }
    return if (m == 0) n + 1
    else if (n == 0) smallAckermann(m - 1, 1)
    else smallAckermann(m - 1, smallAckermann(m, n - 1))
}
```

The input bounds and result contract also establish arithmetic safety; a
termination measure alone does not prevent overflow. This example and its
unbounded overflow-negative counterpart are in
[`termination.kt`](formver.compiler-plugin/testData/diagnostics/language/pure_functions/termination.kt).

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
rejects a second `decreases` in one function and a measure of any other type.

Recursion over shared objects has no measure: a shared node can reach itself
through its `next` chain, so `length(n.next)` need not be smaller.

A measure can decrease through arithmetic rather than subtraction. The
[`remainder_decreases.kt`](formver.compiler-plugin/testData/diagnostics/language/pure_functions/remainder_decreases.kt)
fixture encodes Euclid's recursion with measure `n`: when `m >= 0` and `n > 0`,
`m % n` is nonnegative and strictly smaller than `n`.

### Termination of functions and loops

A function that is not `@Pure` keeps partial correctness: its contract holds
when it returns. With `decreases(...)` in its `preconditions` block it is
termination-checked: the proof also shows that every call returns or exits
abnormally, by an exception or by `Nothing`. Its measures must be `Int`s.

```kotlin
fun countDown(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else countDown(n - 1)
}
```

A termination-checked function obeys these rules; each broken rule is
reported as `TERMINATION_NOT_ESTABLISHED` at the call:

- A call of a function in its recursion cycle, one that can call it back
  through calls of source functions, must decrease its measure. The callee's
  measure, given the call's arguments, must be lexicographically smaller than
  the caller's measure on entry, compared over the positions both have. Each
  function of the cycle needs a measure, and a call of an `inline` function
  of the cycle is rejected, since inlining it leaves no call to check.
  A call of a member that may be overridden is rejected, in the cycle or not.
- Any other source callee must be termination-checked, or be final, not
  recursive, have no loop other than a `for` over a range, contain no rejected
  construct, and call only such functions. A source constructor includes its
  class's property initializers and `init` blocks; a function includes its
  parameters' default values; a top-level function, an object member or a
  constructor includes the initializers of its file, object or companion
  object, which calling it may run. So does the checked function itself. A property
  read is a call of its getter. An open or abstract member may be overridden by
  one that does not terminate, and is never known to terminate. A measure counts
  only when it is a single `decreases` with `Int` measures; the trust report
  lists a measured callee as `skipped` or `@NeverVerify` when nothing verifies it.
- No lambda in it may call a function of its recursion cycle.
- Library calls, opaque calls and library operators among them, are assumed to
  terminate, also when a source callee makes them. The trust report lists each
  as `assumed terminating` under the checked function.

The analysis visits every element of the body and rejects, with
`TERMINATION_NOT_ESTABLISHED` naming the construct, any construct that can run
code and that it does not handle. The `preconditions`, `postconditions`
and `loopInvariants` blocks and `decreases` are not executed and are not
analysed; the arguments of `verify` and similar calls are. The rejected
constructs include:

- callable references, such as `::f`;
- delegated properties, local or read through a member;
- function values: reading one, passing one to a library function, or invoking
  one;
- `==` and `!=` between class instances, which call `equals`, unless one side
  is `null`;
- string templates of class instances, which call `toString`;
- a library call with a receiver or argument whose type is not a primitive, a
  `String`, a lambda or a final library class with such type arguments, since
  the library may call back into source code, as `toString` on an `Any` does;
  this covers a setter's assigned value, and the iterator of a `for` loop over
  anything other than a range;
- a delegation from a source constructor to a library superclass constructor
  other than `Any()`, which may call an overridden member on the new object;
- calls of generated members, such as a data class's `copy`;
- loops inside lambdas;
- naming a source object that has initializers, and reading an entry of a source
  enum;
- anonymous objects, local classes and any other construct not listed as
  handled in `conversion/TerminationGraph.kt`.

A loop declares its variant with `decreases(...)` in its `loopInvariants`
block, with `Int` measures compared lexicographically. Each iteration that goes
back to the loop head, at the end of the body or by `continue`, must make the
variant smaller than at the start of the iteration and the start value
non-negative; otherwise `TERMINATION_NOT_ESTABLISHED` is reported at the loop. A
variant is checked in any function. In a termination-checked function each
`while` or `do`-`while` loop needs one, or `MISSING_TERMINATION_MEASURE` is reported at the
loop; a `for` over a range terminates by construction.

```kotlin
var i = 0
while (i < n) {
    loopInvariants {
        i >= 0
        decreases(n - i)
    }
    i++
}
```

The fixtures in
[`termination/`](formver.compiler-plugin/testData/diagnostics/language/termination/)
cover recursion, mutual recursion, loops and callees, each with failing cases.

Limits: object measures are available only to `@Pure` functions.

### Rejected constructs

Each of these is a compile error:

- A downcast move: `val s: @Unique Sub = base as Sub`.
- A lambda that is not called in place and captures a root annotated
  `@Unique` or `@Borrowed`: `fun f(n: @Unique Node) { later { consume(n) } }`.
- An anonymous object, local class or local function that captures an owned
  root.
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

See the [configuration reference](docs/configuration.md) for Gradle and compiler
options.
