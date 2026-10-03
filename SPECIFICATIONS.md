# Writing Specifications in SnaKt

SnaKt translates Kotlin code with formal specifications to [Viper](https://www.pm.inf.ethz.ch/research/viper.html) for verification. This guide assumes familiarity with Hoare logic; see the [Viper tutorial](http://viper.ethz.ch/tutorial/) if needed.

## Verification Control

By default, SnaKt only verifies functions with Kotlin `contract { }` blocks. To verify functions with SnaKt specifications:

```kotlin
import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify  // Enables verification for this function
fun divide(numerator: Int, denominator: Int): Int {
    preconditions { denominator != 0 }
    return numerator / denominator
}
```

**Annotations:**
- `@AlwaysVerify` — verify this function regardless of plugin settings
- `@NeverVerify` — skip verification even with contracts
- `@NeverConvert` — skip Viper conversion entirely

**Plugin configuration** (in `build.gradle.kts`):
```kotlin
formver {
    verificationTargetsSelection("all_targets")  // Verify all functions
    // or "targets_with_contract" (default) — only Kotlin contract { } blocks
    // or "no_targets" — disable verification
}
```

Note that `@AlwaysVerify` overrides plugin settings.

## Preconditions and Postconditions

```kotlin
@AlwaysVerify
fun abs(x: Int): Int {
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
to other functions. The standard library's compiled inline functions, such as `let`, count as other functions.

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

The check runs when the override is converted. Members without a body, such as abstract and interface members,
have no specification. A `@Pure` function cannot be open or override another; such a function, and a call to it,
is reported as an unsupported construct.

## Loop Invariants

```kotlin
@AlwaysVerify
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

A `for` loop over an `Int` progression written with `until`, `..<`, `..` or `downTo`, optionally followed by
`step`, puts its `loopInvariants` at the start of its body:

```kotlin
@AlwaysVerify
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
range is empty. The body may assume the variable lies within the range. A step that is not positive throws.

## Universal Quantification

Use `forAll<T>` for quantified formulas:

```kotlin
@AlwaysVerify
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
`fun @Unique Node.e()`.

| Site | Annotation | Meaning |
|:-----|:-----------|:--------|
| parameter or extension receiver | none | Shared. The function owns nothing. |
| parameter or extension receiver | `@Borrowed` | Shared, and the function may not store, return or capture the value. |
| parameter or extension receiver | `@Unique` | The caller hands the value over. The argument cannot be used after the call. |
| parameter or extension receiver | `@Unique @Borrowed` | The function owns the value during the call and gives it back. |
| function result | `@Unique` | The caller receives ownership of the result. |
| `var` or `val` property | `@Unique` | The object owns the property's value whenever the object itself is owned. |
| local | `@Unique`, or inferred | The local owns its value. |
| dispatch receiver `this` | cannot be annotated | Shared. |
| class | `@Manual` | Permissions for the class are folded by hand. |
| `@Pure` function parameter | `@Unique` | Borrowed, as `@Unique @Borrowed`. |

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

A constructor call gives a shared object when the class lets `this` escape
while it is constructed. That happens when a constructor body, an `init` block
or a property initializer, of the class or of a superclass, uses `this` other
than to read or write a field or to pass it to a `@Borrowed` parameter: for
example, it calls a member function, reads an open property or one with a
custom getter, stores `this`, or captures it in a lambda that is not inlined.
Such a class compiles as usual, but its objects cannot be owned:

```kotlin
class Parser(first: String) {
    private var depth = 0
    init { track(first) }        // member call on `this`
    private fun track(s: String) { depth += s.length }
}

val p: @Unique Parser = Parser("x")  // error: the construction of 'Parser' calls member 'track' on 'this'
```

`@Unique` on a value type (`Int`, `Boolean`, `Char`, `String`, `Unit`, or their
nullable forms) is rejected, since such values own nothing.

### Unique and shared paths

Through a unique path, `var` reads and writes are real, and the verifier
tracks their values. Through a shared path, every `var` read yields an
arbitrary value and every `var` write is dropped, because other code may hold
the same object.

```kotlin
class Counter(var n: Int)

@AlwaysVerify
fun bump(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.n == old(c.n) + 1 }
    c.n = c.n + 1                 // verifies
}

@AlwaysVerify
fun reset(c: Counter) {
    c.n = 0
    val k = c.n
    verify(k == 0)                // fails: c is shared
}
```

After a call that passes an owned value to a `@Borrowed` parameter, the
caller forgets what it knew about that value's `var` properties, since the
callee may have written them.

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
  `arr[i]++` read and write the elements of a unique array. The index must be
  in bounds; otherwise verification reports a possible index out of bounds.
  None of them moves the array.
- On a shared array, an element read yields an arbitrary `Int` and an element
  write is dropped. In a specification, an element read needs a unique array.
- `IntArray(n)` creates an array of `n` zeros. As with any constructor call, a
  local holding it owns it only when declared `@Unique IntArray`.
- `IntArray(n) { init }` runs `init` on each index from `0` to `n - 1` in
  order, inlined into a loop. When the body of `init` is a single pure
  expression, every element is known to equal it at its index afterwards;
  otherwise only the size is known. The loop keeps ownership of the unique
  data around it, but, as for any loop, facts about the contents of that data
  are lost.

```kotlin
@AlwaysVerify
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
@AlwaysVerify
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

A `@Pure` function cannot return an alias into data its caller still owns. Its
result cannot be `@Unique`, and when it has a `@Unique` parameter its result
must be a value type.

```kotlin
@Pure
fun isSorted(n: @Unique Node?): Boolean {
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
fun size(t: @Unique Tree?): Int = if (t == null) 0 else 1 + size(t.l) + size(t.r)

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

### Rejected constructs

Each of these is a compile error:

- A downcast move: `val s: @Unique Sub = base as Sub`.
- A lambda, anonymous object, local class or local function that captures an
  owned root, unless Kotlin inlines it in place:
  `val f = { consume(n) }`.
- `@Unique` on a declaration whose type is a type parameter:
  `class Box<T>(var item: @Unique T)`.
- An override that does not repeat the `@Unique` and `@Borrowed` annotations
  of the declaration it overrides, on parameters, receiver and result.
- An `actual` declaration that does not repeat the `@Unique` and `@Borrowed`
  annotations of its `expect` declaration, on parameters, receiver and result.
- `try` in a function that owns anything anywhere: a signature with `@Unique`
  or `@Borrowed`, or any owned local, in or outside the `try`. Passing a fresh
  object straight to a call, as in `try { consume(Node(1, null)) }`, is
  allowed. `try` with `finally` is rejected in every function.
- Using a unique path after a member or accessor call on it. `this` is always
  shared, so `n.describe()` consumes `n`. Write code over unique data as
  top-level or extension functions with an annotated receiver.
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

## Additional Plugin Options

```kotlin
formver {
    errorStyle("user_friendly")  // or "original_viper", "both"
    logLevel("only_warnings")    // or "short_viper_dump", "full_viper_dump"
}
```
