// FULL_JDK
// TRUST_REPORT
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>half<!>(x: Int): Int = x / 2

fun <!VIPER_TEXT!>halfTwice<!>(x: Int): Int = half(half(x))

fun <!VIPER_TEXT!>loops<!>(n: Int) {
    var i = 0
    while (i < n) i++
}

fun <!VIPER_TEXT!>callsLoops<!>(n: Int) = loops(n)

fun <!VIPER_TEXT!>recurses<!>(n: Int): Int = if (n <= 0) 0 else recurses(n - 1)

fun <!VIPER_TEXT!>measured<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases(n)
    }
    return if (n == 0) 0 else measured(n - 1)
}

// Non-recursive, loop-free callees and measured callees terminate; the others may not.
fun <!VIPER_TEXT!>caller<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases()
    }
    <!TERMINATION_NOT_ESTABLISHED!>loops(n)<!>
    <!TERMINATION_NOT_ESTABLISHED!>callsLoops(n)<!>
    val r = <!TERMINATION_NOT_ESTABLISHED!>recurses(n)<!>
    <!OPAQUE_CALL!>println(r)<!>
    measured(n)
    return halfTwice(n)
}

open <!VIPER_TEXT!>class Base<!> {
    open fun <!VIPER_TEXT!>step<!>(): Int = 0
}

// An override of `step` may not terminate.
fun <!VIPER_TEXT!>callsOpen<!>(b: Base): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>b.step()<!>
}

fun <!VIPER_TEXT!>callsFunctionObject<!>(f: () -> Int): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!><!TERMINATION_NOT_ESTABLISHED!>f<!>()<!>
}

abstract class Shape {
    abstract fun <!VIPER_TEXT!>area<!>(): Int
}

// An implementation of `area` may not terminate.
fun <!VIPER_TEXT!>callsAbstract<!>(s: Shape): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>s.area()<!>
}

// The bound of a range is evaluated before the loop, so its call is checked too.
fun <!VIPER_TEXT!>rangeBound<!>(n: Int): Int {
    preconditions {
        n >= 0
        decreases()
    }
    var last = 0
    for (i in 0 until <!TERMINATION_NOT_ESTABLISHED!>recurses(n)<!>) {
        last = i
    }
    return last
}

// A function without a measure assumes nothing about the termination of its library calls.
fun <!VIPER_TEXT!>uncheckedLibraryCall<!>() {
    <!OPAQUE_CALL!>println(1)<!>
}

fun <!VIPER_TEXT!>spin<!>(x: Int): Int {
    while (x >= 0) {
    }
    return x
}

// The analysis does not follow a callable reference.
fun <!VERIFICATION_SKIPPED!>passesReference<!>(n: Int): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!><!TERMINATION_NOT_ESTABLISHED!>listOf(n).map(<!TERMINATION_NOT_ESTABLISHED, UNSUPPORTED_FEATURE!>::spin<!>)<!>.size<!>
}

<!VIPER_TEXT!>class Initialized<!> {
    val x: Int = viaInitializer(0)
}

// Constructing `Initialized` runs its initializer, which calls `viaInitializer` again.
fun <!VIPER_TEXT!>viaInitializer<!>(n: Int): Int {
    preconditions { decreases(n) }
    return <!TERMINATION_NOT_ESTABLISHED!>Initialized()<!>.x
}

<!VIPER_TEXT!>class Box<!>(val v: Int)

// `==` on class instances calls `equals`, which the analysis does not follow.
fun <!VIPER_TEXT!>comparesBoxes<!>(a: Box, b: Box): Boolean {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>a == b<!>
}

fun <!VIPER_TEXT!>templateOfBox<!>(b: Box): String {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>"$b"<!>
}

fun <!VIPER_TEXT!>boxToString<!>(b: Any): String {
    preconditions { decreases() }
    return <!OPAQUE_CALL, TERMINATION_NOT_ESTABLISHED!>b.toString()<!>
}

interface HasSize {
    val size: Int
}

// An implementation of the getter of `size` may not terminate.
fun <!VIPER_TEXT!>readsAbstractProperty<!>(h: HasSize): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>h.size<!>
}

<!VIPER_TEXT!>object Registry<!> {
    val first: Int = spin(0)
}

// Reading `Registry` runs its initializer, which calls `spin`.
fun <!VIPER_TEXT!>readsObject<!>(): Int {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!><!TERMINATION_NOT_ESTABLISHED!>Registry<!>.first<!>
}

fun <!VIPER_TEXT!>passesFunctionValue<!>(f: (Int) -> Int): List<Int> {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>listOf(1).map(<!TERMINATION_NOT_ESTABLISHED!>f<!>)<!>
}

fun <!VERIFICATION_SKIPPED!>booleanMeasure<!>(b: Boolean) {
    preconditions { decreases(<!INVALID_TERMINATION_MEASURE!>b<!>) }
}

fun <!VERIFICATION_SKIPPED!>verifiesSpin<!>() {
    preconditions { decreases() }
    verify(<!PURITY_VIOLATION!><!TERMINATION_NOT_ESTABLISHED!>spin(0)<!> == 0 || true<!>)
}

<!VIPER_TEXT!>class WithCompanion<!> {
    companion <!VIPER_TEXT!>object<!> {
        val seed: Int = spin(0)
    }
}

// Constructing `WithCompanion` may first run its companion's initializer.
fun <!VIPER_TEXT!>constructsWithCompanion<!>(): WithCompanion {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>WithCompanion()<!>
}

enum class Level(val v: Int) {
    LOW(spin(0)),
}

fun <!VIPER_TEXT!>readsEnumEntry<!>(): Level {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>Level.LOW<!>
}

data <!VIPER_TEXT!>class Pair2<!>(val a: Int, val b: Int)

// The generated `copy` is neither library code nor analysable source.
fun <!VIPER_TEXT!>copiesDataClass<!>(p: Pair2): Pair2 {
    preconditions { decreases() }
    return <!TERMINATION_NOT_ESTABLISHED!>p.copy(a = 1)<!>
}

fun <!VIPER_TEXT!>nullableToString<!>(b: Box?): String {
    preconditions { decreases() }
    return <!OPAQUE_CALL, TERMINATION_NOT_ESTABLISHED!>b.toString()<!>
}

fun <!VIPER_TEXT!>loopInLambda<!>(xs: List<Int>): Int {
    preconditions { decreases() }
    return <!OPAQUE_CALL, TERMINATION_NOT_ESTABLISHED!>xs.count { <!TERMINATION_NOT_ESTABLISHED!>while (true) {
    }<!>; true }<!>
}

<!VIPER_TEXT!>class MyList<!>(c: Collection<Int>) : ArrayList<Int>(c)

// The delegation to the library constructor passes a value that a source class may implement.
fun <!VIPER_TEXT!>delegatesWithOpenValue<!>(c: Collection<Int>): Int {
    preconditions { decreases() }
    <!TERMINATION_NOT_ESTABLISHED!>MyList(c)<!>
    return 0
}

// The library setter receives a value of a source class.
fun <!VIPER_TEXT!>setsSourceValue<!>(r: java.util.concurrent.atomic.AtomicReference<Box>, b: Box) {
    preconditions { decreases() }
    <!TERMINATION_NOT_ESTABLISHED!>r.plain = b<!>
}

// A `for` loop over a value that is not a range calls the iterator's members.
fun <!VIPER_TEXT!>iteratesIterable<!>(xs: Iterable<Int>) {
    preconditions { decreases() }
    <!MISSING_TERMINATION_MEASURE!>for (x in <!OPAQUE_CALL, TERMINATION_NOT_ESTABLISHED, TERMINATION_NOT_ESTABLISHED, TERMINATION_NOT_ESTABLISHED, UNCHECKED_LIBRARY_CALL, UNCHECKED_LIBRARY_CALL!>xs<!>) {
    }<!>
}

<!VIPER_TEXT!>class Failure<!> : Exception("failure")

// The library superclass constructor may call an overridden member of the new object.
fun <!VIPER_TEXT!>constructsException<!>(): Int {
    preconditions { decreases() }
    <!TERMINATION_NOT_ESTABLISHED!>Failure()<!>
    return 0
}
