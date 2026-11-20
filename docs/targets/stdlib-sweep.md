# Kotlin stdlib sweep: verifier-driven triage

Use this when choosing stdlib code to verify, or when prioritising verifier gaps
by how often they block real library code. It records what the verifier flagged
on pure-Kotlin functions of JetBrains/kotlin `v2.3.0`, and how each flagged
obligation was triaged.

## Method

Each function is copied from `git show v2.3.0:<path>` into a fixture with only
the contract its KDoc (or an explicit source comment such as "By contract
bytesPerLine and bytesPerGroup are > 0") states, then verified. Every failed
obligation is classified as:

- defect: a violated contract with a plain JVM reproducer on Kotlin 2.3.0;
- intentional wrapping: two's-complement overflow the source relies on and
  checks afterwards; rerun under `@WrappingIntArithmetic`,
  `@WrappingLongArithmetic` or `@WrappingNarrowing`;
- missing precondition or postcondition: a fact the callers guarantee or rely on
  that no KDoc states; rerun with it added and labelled in the fixture header;
- missing proof annotation: a loop invariant or `verify` hint;
- fixture spec error: a mistake in the contract written for the sweep;
- verifier gap: the verifier cannot express or decide the obligation.

Each fixture header lists the facts it adds beyond the KDoc contract.
Adaptations needed to compile the code standalone (an instance becomes
parameters, `ULong` becomes its `Long` bit pattern, an inline `() -> Nothing`
callback becomes a `throw`) are listed in the header too.

Fixtures that verify are under `formver.compiler-plugin/testData/programs/ports/stdlib/`
and run with the rest of the suite. The others are under
`formver.compiler-plugin/testData/experiments/stdlib-sweep/`; their diagnostic
markers record the outcome of their last run. Experiments are not discovered by
the fixture runner; to rerun one, copy it under `testData/programs/` and run
`agent-scripts/test.sh --update-goldens --record-outcomes <stem>`.

## Results

No function produced a defect: every flagged obligation is intentional
wrapping, a missing precondition or annotation, a fixture spec error, or a
verifier gap. There are therefore no reproducers and no negative fixtures.

| Function (source file) | Flagged with the KDoc contract | Triage | Final outcome |
|---|---|---|---|
| `AbstractList.newCapacity` (`collections/AbstractList.kt`) | `INTEGER_OVERFLOW` on `oldCapacity + (oldCapacity shr 1)` | intentional wrapping ("overflow-conscious") | verifies with wrapping and `result >= minCapacity` (`NewCapacityWrapping.kt`) |
| `ArrayList.ensureExtraCapacity` / `ensureCapacityInternal` (`native-wasm/.../ArrayList.kt`) | postcondition `result >= minCapacity` not established; `INTEGER_OVERFLOW` in `newCapacity` | `newCapacity`'s KDoc states no result; `length + n` wraps on purpose into the `OutOfMemoryError` check | verifies with wrapping and `newCapacity`'s postcondition (`ArrayListCapacity.kt`) |
| `Long.addMillisWithoutOverflow` (`time/Duration.kt`) | `INTEGER_OVERFLOW` on `this + other`; postcondition; `sameSign` times out | missing precondition (operands in `±MAX_MILLIS`); gap (`xor` of two variables) | verifies with the precondition and `sameSign` inline (`programs/ports/stdlib/AddMillisWithoutOverflow.kt`) |
| `durationOfMillisNormalized`, `addValuesMixedRanges` (`time/Duration.kt`) | overflow in `millisToNanos`; representation postcondition | missing preconditions on private helpers; the source comment "resultMillis will never be INVALID" needs `addMillisWithoutOverflow` to say a finite operand never yields `INVALID_RAW_VALUE` | both verify against `Duration.fromRawValue`'s representation check (`programs/ports/stdlib/DurationNormalize.kt`) |
| `formattedStringLength` (4 and 7 parameters) (`text/HexExtensions.kt`) | `VERIFICATION_TIMEOUT` on both | gap (nonlinear Long arithmetic) | unproved (`HexFormatLength.kt`) |
| `checkFormatLength` (`text/HexExtensions.kt`) | `toULong` in the exception message has no model | does not affect the result | verifies (`programs/ports/stdlib/CheckFormatLength.kt`) |
| `parsedByteArrayMaxSize`, `charsPerSet`, `wholeElementsPerSet` (`text/HexExtensions.kt`) | timeouts on the first two; `DIVISION_BY_ZERO` and `INTEGER_OVERFLOW` in `wholeElementsPerSet` | gap (nonlinear); missing preconditions (non-negative separator lengths, `charsPerSet <= Int.MAX_VALUE` from its callers) | unproved (`HexParsedSize.kt`) |
| `Base64.encodeSize` (`io/encoding/Base64.kt`) | `INTEGER_OVERFLOW` on `groups * symbolsPerGroup` and both `+=` | intentional wrapping, caught by the `size < 0` checks; Int `*` has no wrapping mode | verifies `result >= 0` with the product written as a Long truncated by `@WrappingNarrowing` (`Base64EncodeSizeWrapping.kt`) |
| `Base64.decodeSize` (`io/encoding/Base64.kt`) | `INTEGER_OVERFLOW` on `symbols -= endIndex - index` and `symbols--`; `result >= 0` | missing loop invariant; with `endIndex - index <= symbols` the invariant fails at the `break` exit | unproved: gap (invariant required at `break` without ghost state) (`Base64DecodeSize.kt`) |
| `String.toIntOrNull(radix)` with `checkRadix` (`text/StringNumberConversions.kt`) | `INTEGER_OVERFLOW` on `result *= radix` and `-result` | missing loop invariant | verifies with invariants `limit <= result <= 0` (`programs/ports/stdlib/ToIntOrNull.kt`) |
| `ulongCompare` (`jvm/.../UnsignedJVM.kt`) | none | — | verifies (`UnsignedLong.kt`) |
| `ulongDivide` (`jvm/.../UnsignedJVM.kt`) | `INTEGER_OVERFLOW` on `quotient * divisor` and the subtraction | intentional wrapping | verifies with `@WrappingLongArithmetic`; the postcondition covers only the case `v1 >= 0 && v2 > 0`, since an unsigned quotient needs integers wider than Long (`UnsignedLong.kt`) |
| `safeAddOrElse` (`time/Instant.kt`) | `INTEGER_OVERFLOW` on `a + b` | intentional wrapping | times out under wrapping: gap (`xor` of two variables) (`InstantSafeAdd.kt`) |
| `safeMultiplyOrElse`, `Instant.toEpochMilliseconds` (`time/Instant.kt`) | timeouts | gap (nonlinear Long) | unproved (`InstantArithmetic.kt`) |
| `Instant.fromEpochMilliseconds` (`time/Instant.kt`) | `floorDiv` and `mod` have no model; `INTEGER_OVERFLOW` and range postcondition on the nanosecond part | gap | the seconds part verifies through its clamp; the nanosecond part verifies with `mod` behind a stand-in carrying its KDoc contract (`InstantFromMillis.kt`) |
| `Long.floorDiv(Long)`, `Long.mod(Long)` (`util/FloorDivMod.kt`) | timeouts | gap (`xor`, `and`, `or` and `shr` of variables) | unproved (`FloorDivMod.kt`) |
| `List.binarySearch(element, fromIndex, toIndex)` (`collections/Collections.kt`) | `List.get` bounds in the loop and postcondition; `INTEGER_OVERFLOW` on `low + high` and `-(low + 1)` | missing invariants; `rangeCheck` states no postcondition; `(low + high).ushr(1)` wraps on purpose ("safe from overflows"); unary minus has no wrapping mode | verifies except `-(low + 1)`, with `@WrappingIntArithmetic`, `rangeCheck`'s postcondition, loop invariants that repeat sortedness, and the insertion point decoded in Long (`BinarySearch.kt`) |

`-(low + 1)` in `binarySearch` overflows only for `toIndex == Int.MAX_VALUE`,
which needs a list of that size. The JVM result is then `Int.MIN_VALUE`, which
decodes to insertion point `Int.MAX_VALUE` as the KDoc requires, so this is not
a defect.

## Verifier gaps, ranked by functions blocked

| Gap | Functions blocked | Where |
|---|---:|---|
| Nonlinear `Long` arithmetic times out at 300 s | 6 | both `formattedStringLength`, `parsedByteArrayMaxSize`, `charsPerSet`, `safeMultiplyOrElse`, `toEpochMilliseconds` |
| Bitwise operations on two non-literal operands time out | 4 | `sameSign`, `safeAddOrElse`, `Long.floorDiv`, `Long.mod` |
| Stdlib `floorDiv` and `mod` have no model | 1 | `fromEpochMilliseconds` |
| Int `*` and unary minus have no wrapping mode | 2 | `encodeSize`, `binarySearch` |
| A loop invariant must hold at a `break` exit, with no ghost state to tell the exits apart | 1 | `decodeSize` |
| Unsigned 64-bit results cannot be stated: specifications have no integers wider than `Long` | 1 | `ulongDivide` (partial postcondition) |

Diagnostics that cost triage time without blocking:

- A `List.get` bounds failure inside a specification is reported as
  `VIPER_VERIFICATION_ERROR [application.precondition/assertion.false]` with
  Viper names (`ListModel_g_size`), not as a Kotlin collection-bounds obligation.
- A fixture whose specification has a frontend type error (`b >= 0L implies (...)`
  parses as `b >= (0L implies ...)`) also reports `INTERNAL_ERROR`
  `NullPointerException: null cannot be cast to non-null type FirFunctionSymbol`
  on that function and on an unrelated caller in the same file.

## Reproduction

Copy an experiment under `testData/programs/`, or name a fixture under
`programs/ports/stdlib/`, then:

```
agent-scripts/test.sh --update-goldens --record-outcomes <stem>
agent-scripts/test.sh --stop
```

The kept test JVM keeps Z3 processes alive after a run ends; `--stop` ends them.
