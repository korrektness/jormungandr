// LOCALITY_CHECK_ONLY
// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.formver.plugin.Borrowed

class A {
    @Borrowed
    fun inspect() {}

    val value: Any = Any()
}

fun @Borrowed A.inspectExtension() {}

fun nondet(): Boolean = false

fun borrow(x: @Borrowed A) {}
fun useAny(x: Any) {}

fun useBorrowed(f: @Borrowed () -> Unit) {
    //...
}

fun useGlobalAndBorrowed(g: () -> Unit, f: @Borrowed () -> Unit) {
    //...
}

fun (@Borrowed () -> Unit).callTwice() {
    this()
    this()
}

fun (() -> Unit).callTwiceGlobally() {
    this()
    this()
}

fun useContextFunction(f: @Borrowed context(A) () -> Unit) {
    //...
}

context(c: @Borrowed () -> Unit)
fun runFromContext() {
    c()
}

fun `bound references retain borrowed receivers`(x: @Borrowed A) {
    val member: () -> Unit = <!LOCALITY_MISMATCH!>x::inspect<!>
    val extension: () -> Unit = <!LOCALITY_MISMATCH!>x::inspectExtension<!>
    val property: Any = <!LOCALITY_MISMATCH!>x::value<!>

    val borrowedMember: @Borrowed () -> Unit = x::inspect
    val borrowedExtension: @Borrowed () -> Unit = x::inspectExtension
    val borrowedProperty: @Borrowed Any = x::value
    borrowedMember()
    borrowedExtension()
}

fun `bound references to global receivers may escape`(x: A) {
    val member: () -> Unit = x::inspect
    val extension: () -> Unit = x::inspectExtension
    val property: Any = x::value
}

fun `unbound references do not capture receivers`() {
    val member: (A) -> Unit = A::inspect
    val extension: (A) -> Unit = A::inspectExtension
    val property: Any = A::value
}

fun `inferred reference retains borrowed receiver`(x: @Borrowed A) {
    val reference = x::inspect
    useBorrowed(reference)
    val escaped: () -> Unit = <!LOCALITY_MISMATCH!>reference<!>
}

fun `return bound reference retains borrowed receiver`(x: @Borrowed A): () -> Unit =
    <!LOCALITY_MISMATCH!>x::inspect<!>

fun `capture local from global lambda capturing`(x: @Borrowed Any) {
    {
        var y = x
    }
}

fun `capture local from local receiver lambda capturing`(x: @Borrowed Any) {
    {
        var y = x
    }.callTwice()
}

fun `capture local from nested global lambda`(x: @Borrowed Any) {
    {
        <!LOCALITY_MISMATCH!>{
            var y = x
        }<!>
    }
}

fun `capture local from nested local lambda`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = {
        var y = x
    }
}

fun `capture local from doubly nested local lambda`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = {
        val g: @Borrowed () -> Unit = {
            var y = x
        }
    }
}

fun `nested local-only capture should not localize outer lambda`() {
    val f: () -> Unit = {
        val g = {
            val y: @Borrowed A = A()
            borrow(y)
        }

        g()
    }
}

fun `calling local function reference declared in same scope should not localize outer lambda`() {
    val f: () -> Unit = {
        val x: @Borrowed A = A()

        fun local() {
            borrow(x)
        }

        val ref = ::local
        ref()
    }
}

fun `capture local from triply nested local lambda`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = {
        val g: @Borrowed () -> Unit = {
            val h: @Borrowed () -> Unit = {
                val y: @Borrowed Any = x
            }
        }
    }
}

fun `capturing a borrowed local from a local lambda is allowed`(x: @Borrowed Any) {
    val borrowX: @Borrowed () -> Unit = {
        val y: @Borrowed Any = x
    }

    borrowX()
}

fun `capturing a borrowed local from a local lambda as an argument is allowed`(x: @Borrowed Any) {
    useBorrowed {
        val y: @Borrowed Any = x
    }
}

fun `capture local property into local lambda`() {
    val z: @Borrowed Any = Any()

    val f: @Borrowed () -> Unit = {
        val y: @Borrowed Any = z
    }
}

fun `capture value declared in enclosing local lambda`() {
    val f: @Borrowed () -> Unit = {
        val z: @Borrowed Any = Any()

        val g: @Borrowed () -> Unit = {
            val y: @Borrowed Any = z
        }
    }
}

fun `capture into local lambda passed to borrowed slot`(x: @Borrowed Any) {
    useGlobalAndBorrowed({ }) {
        val y: @Borrowed Any = x
    }
}

fun `capture inside control flow in local lambda body`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = {
        val y: @Borrowed Any = if (nondet()) x else x
    }
}

fun `capture into local lambda bound through if`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = if (nondet()) {
        { val y: @Borrowed Any = x }
    } else {
        { }
    }
}

fun `capture into global lambda bound through if`(x: @Borrowed Any) {
    val f: () -> Unit = <!LOCALITY_MISMATCH!>if (nondet()) {
        { val y = x }
    } else {
        { }
    }<!>
}

fun `capture into local lambda bound through when initializer`(x: @Borrowed Any) {
    val f: @Borrowed () -> Unit = when {
        nondet() -> { { val y: @Borrowed Any = x } }
        else -> { { } }
    }
}

fun `capture into local lambda bound through try as argument`(x: @Borrowed Any) {
    useBorrowed(
        try {
            { val y: @Borrowed Any = x }
        } catch (_: Throwable) {
            { }
        }
    )
}

fun `capture into local lambda bound through when as argument`(x: @Borrowed Any) {
    useBorrowed(
        when {
            nondet() -> { { val y: @Borrowed Any = x } }
            else -> { { } }
        }
    )
}

fun `capture into local lambda bound through cast as argument`(x: @Borrowed Any) {
    useBorrowed({ val y: @Borrowed Any = x } <!USELESS_CAST!>as () -> Unit<!>)
}

fun `capture into borrowed lambda used as receiver`(x: @Borrowed A) {
    { val y: @Borrowed A = x }.callTwice()
}

fun `capture into global lambda used as receiver`(x: @Borrowed A) {
    <!LOCALITY_MISMATCH!>{ val y: @Borrowed A = x }<!>.callTwiceGlobally()
}

fun `capture into local lambda with receiver type`(x: @Borrowed A) {
    val f: @Borrowed A.() -> Unit = {
        val y: @Borrowed A = x
    }
}

fun `capture into local lambda with context type`(x: @Borrowed A) {
    val f: @Borrowed context(A) () -> Unit = {
        val y: @Borrowed A = x
    }
}

fun `capture into borrowed context lambda passed as argument`(x: @Borrowed A) {
    useContextFunction {
        val y: @Borrowed A = x
    }
}

fun `provide capturing lambda as borrowed context via with`(x: @Borrowed A) {
    with(<!LOCALITY_MISMATCH!>{ val y: @Borrowed A = x }<!>) {
        runFromContext()
    }
}

fun `resolve captured local owner from nested lambda`(x: @Borrowed A) {
    val x: @Borrowed A = x

    run {
        borrow(x)
    }

    borrow(x)
}

fun `resolve shadowing local's owner first`(x: @Borrowed A) {
    val x: @Borrowed A = x

    run {
        val x: @Borrowed A = A()
        borrow(x)
    }

    borrow(x)
}

fun `assign local to local in lambda`(x: @Borrowed Any) {
    { y: Any ->
        var z: @Borrowed Any = x
    }
}

fun `assign local if-expression to local in lambda`(x: @Borrowed Any) {
    { y: Any ->
        var z: @Borrowed Any = if (false) { x } else { Any() }
    }
}

fun `assign local nested control-flow to global in lambda loop`(x: @Borrowed Any) {
    {
        var z: Any = Any()

        while (true) {
            z = <!LOCALITY_MISMATCH!>when {
                false -> try { x } catch (_: Throwable) { Any() }
                else -> Any()
            }<!>

            z = <!LOCALITY_MISMATCH!>if (false) {
                x
            } else {
                try { Any() } catch (_: Throwable) { x }
            }<!>
        }
    }
}

fun `assign merged local owners to global`(x: @Borrowed Any) {
    { y: @Borrowed Any ->
        var z: Any = <!LOCALITY_MISMATCH!>if (false) { x } else { y }<!>
    }
}

fun `capture local into local anonymous object`(x: @Borrowed A) {
    val o: @Borrowed Any = object {
        fun run() {
            borrow(<!INVALID_LOCALITY_CAPTURE!>x<!>)
        }
    }
}

fun `capture local into global anonymous object`(x: @Borrowed A) {
    val o: Any = object {
        fun run() {
            borrow(<!INVALID_LOCALITY_CAPTURE!>x<!>)
        }
    }
}

fun `pass capturing anonymous object as global argument`(x: @Borrowed A) {
    useAny(object {
        fun run() {
            borrow(<!INVALID_LOCALITY_CAPTURE!>x<!>)
        }
    })
}

fun `pass outer local as local argument in lambda`(x: @Borrowed A) {
    {
        borrow(x)
    }
}

fun `return local value from lambda body`(x: @Borrowed Any) {
    run<Any> {
        <!LOCALITY_MISMATCH!>x<!>
    }
}

fun `throw local value from lambda`(x: @Borrowed Throwable) {
    run {
        throw <!LOCALITY_MISMATCH!>x<!>
    }
}

fun `capture local in inner class property`(x: @Borrowed Any) {
    class I {
        val y = <!INVALID_LOCALITY_CAPTURE!>x<!>
    }

    val i = I()
}

fun `capture local in inner class method`(x: @Borrowed Any) {
    class I {
        fun g() {
            val y = <!INVALID_LOCALITY_CAPTURE!>x<!>
        }
    }

    val i = I()
}
