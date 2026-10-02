# Uniqueness Module

This module implements the `@Unique` checker for the SnaKt compiler plugin (`formver.compiler-plugin:uniqueness`).

## Scope

The module is responsible for:
- interpreting `@Unique` as a FIR type attribute
- resolving declared uniqueness of symbols/expressions
- tracking the uniqueness state across the control flow of a function
- reporting uniqueness diagnostics (mismatch, use-after-move, escaping/leaking moved subpaths, unique-argument collisions)

`@Borrowed` behavior is provided by the locality module and consumed here to restore borrowed values after calls.

## Strategy

The checker tracks two kinds of uniqueness:

1. **Declared uniqueness (flow-insensitive)**
   - Tracks uniqueness requirements from types and declarations.
   - Used by type-like assignment/call/return/throw compatibility checks.

2. **State uniqueness (aka "actual" uniqueness)**
   - Tracks which concrete access paths are moved at each CFG point.
   - Used for use-after-move and consistency checks (escape/exit).

## Core Mechanism

### Uniqueness attribute and attribute checking

- `UniquenessAttributeExtension.kt` maps the `@Unique` annotation to `UniquenessAttribute`.
- `Uniqueness.kt` defines the actual uniqueness lattice:
  - `Unique < Unknown < Shared < Moved`
- `TypeRefUniquenessAttributeChecker.kt` restricts valid annotation targets to local variables and function parameters.

### Path models

- Paths are represented as symbol sequences (`Path.kt`).
- Both access information and uniqueness state are represented by tries (`PathTrie.kt`):
  - `AccessState = PathTrie<Access>`
  - `UniquenessState = PathTrie<Uniqueness>`
- `ExpressionAccessStateResolver.kt` extracts the paths touched by each expression.

### CFG uniqueness state analysis

`GraphUniquenessStatesAnalyzer.kt` computes a fixed point over the following CFG nodes:

- **Initialization**
  - The initial state contains the uniqueness information of the function's parameters.

- **Variable declaration/assignment**
  - Project RHS' uniqueness substate into LHS' path.
  - Initialize LHS path to declared uniqueness.
  - Move RHS access paths, unless the declaration is in a read-only context.

- **Function-call enter**
  - Move all receivers (including context receivers) and value arguments.
  - Calls to `@Pure` functions and calls in a read-only context move nothing.

- **Function-call exit**
  - Re-initialize arguments/receiver whose required locality is local (`@Borrowed`).

- **Return / throw**
  - Move the escaped expression paths.

- **Merges**
  - Join incoming states path-wise.

- **Subgraphs**
  - Default arguments and lambdas called in place (arguments of inline functions, and lambdas with a `callsInPlace`
    contract) are analyzed as part of the enclosing function's flow. The control-flow graph gives such a lambda a
    back edge when it may run more than once. A lambda called in place starts with its own parameters at their
    declared uniqueness.
  - Other lambdas, anonymous objects and local classes are analyzed as separate functions. The function checkers
    skip lambdas called in place, since the enclosing function's analysis covers them.

`ReadOnlyContext.kt` defines the read-only contexts.

### Diagnostics

Checkers consume the above analyses:

- Type-compatibility checkers (`UniquenessTypeCheckers.kt`)
  - assignment/call/qualified-access/return/throw mismatches
- `FunctionUseAfterMoveChecker.kt`
  - reports `INVALID_MOVED_ACCESS`
- `FunctionEscapeUniquenessConsistencyChecker.kt`
  - reports moved subpaths on escaping values
- `FunctionExitUniquenessConsistencyChecker.kt`
  - reports moved subpaths left in borrowed locals at function exit
  - also checks the parameters of `@Pure` functions, as its KDoc describes
- `PureFunctionUniqueResultChecker.kt`
  - reports a `@Unique` result on a `@Pure` function
- `ExpressionArgumentUniquenessCollisionChecker.kt`
  - reports duplicate/overlapping unique arguments in one call

### Facade for the converter

`UniquenessFacts.kt` is the session component through which the core converter reads the analysis.
`analysis(function)` returns `null` when the uniqueness or locality checkers report an error in the function; it
decides this by running those checkers over the function against a reporter that only counts errors. Otherwise it
returns a `FunctionUniquenessAnalysis` that maps FIR elements to the uniqueness state before and after them, and
resolves the declared uniqueness of symbols.

## Current Test Coverage

Uniqueness diagnostics are primarily covered by:

`formver.compiler-plugin/testData/diagnostics/uniqueness_checker/`

Current scenarios include:
- annotation targeting
- local/property assignments
- call argument checking and collisions
- aliasing behavior
- receiver/context parameter behavior
- return/throw escape consistency
- loops, `when`, `try/catch`, nullable flows
- lambdas called in place
- constructor/operator cases

## Current Limitations / Untested Behavior

Known limitations in current code/tests:

- **Caught `throw` handling is conservative**
  - Throw expressions are treated as escapes/exits even when locally caught.
  - See TODOs in:
    - `FunctionEscapeUniquenessConsistencyChecker.kt`
    - `FunctionExitUniquenessConsistencyChecker.kt`
  - Also documented in `uniqueness_checker/throw.kt`.

- **Invoke-call uniqueness contracts**
  - `ExpressionArgumentUniquenessesMapper.kt` currently uses `TODO: Implement uniqueness contract resolution`.
  - This means that the parameters of higher-order functions cannot be specified as `@Unique`.

- **Property path receiver support**
  - `QualifiedAccessPathReceiverResolver.kt` currently handles only property accesses with backing fields through dispatch receiver.

- **Interaction with closures**
  - A lambda that is not called in place is analyzed as a separate function in which captured roots have no entry,
    so they count as `Unique`. Capturing and moving a unique root there is not reported.
  
