// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Law1<!>(var f: Int) {
    init { require(f > 0) }
    @Law @Pure @Unique fun inv(): Boolean = f > 0
}

// A law-free class's construction is verified, so a shared write to a law-bound field is rejected.
class CtorWriter<!VERIFICATION_SKIPPED!>(c: @Borrowed Law1)<!> {
    init { <!UNSUPPORTED_FEATURE!>c.f = 0<!> }
}

<!VERIFICATION_SKIPPED!>class InitializerWriter<!>(c: @Borrowed Law1) {
    val x: Int = run { <!INVALID_LOCALITY_CAPTURE!>c<!>.f = 0; 1 }
}

fun <!VIPER_TEXT!>ctorClient<!>(c: @Unique Law1) {
    CtorWriter(c)
    verify(c.f > 0)
}

fun <!VIPER_TEXT!>initializerClient<!>(c: @Unique Law1) {
    InitializerWriter(c)
    verify(c.f > 0)
}

fun <!VIPER_TEXT!>zap<!>(t: @Borrowed PhaseTwo) { t.a = 0 }
fun <!VIPER_TEXT!>zapOne<!>(t: @Borrowed PhaseOne) { t.a = 0 }
fun <!VIPER_TEXT!>zapSpec<!>(t: @Borrowed InSpec): Boolean { t.a = 0; return true }
fun <!VIPER_TEXT!>zapExt<!>(t: @Borrowed ExtWrite) { t.a = 0 }
fun ExtWrite.<!VIPER_TEXT!>zapMe<!>() { a = 0 }

<!VIPER_TEXT!>class PhaseTwo<!>(var a: Int) {
    init { zap(this) }
}

// A construction-discipline violation leaves the constructor unverified and its callers without field facts.
<!VERIFICATION_SKIPPED!>class PhaseOne<!>(var a: Int) {
    init { zapOne(this) }
    val later = 1
}

// A specification call is construction code: the write analysis sees `this` passed inside it.
class InSpec<!VERIFICATION_SKIPPED!>(var a: Int)<!> {
    init { verify(<!PURITY_VIOLATION!>zapSpec(this)<!>) }
}

<!VIPER_TEXT!>class ExtWrite<!>(var a: Int) {
    init { zapMe() }
}

<!VIPER_TEXT!>class InPrecondition<!>(var a: Int) {
    init { preconditions { run { a = 0; true } } }
}

fun <!VIPER_TEXT!>phaseTwoClient<!>() {
    val t: @Unique PhaseTwo = PhaseTwo(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>t.a == 1<!>)
}

fun <!VIPER_TEXT!>phaseOneClient<!>() {
    val t: @Unique PhaseOne = PhaseOne(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>t.a == 1<!>)
}

fun <!VIPER_TEXT!>phaseOneLater<!>() {
    val t: @Unique PhaseOne = PhaseOne(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>t.later == 1<!>)
}

fun <!VIPER_TEXT!>inSpecClient<!>() {
    val t: @Unique InSpec = InSpec(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>t.a == 1<!>)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>extClient<!>() {
    val t: @Unique ExtWrite = <!SHARED_CONSTRUCTION_MISMATCH!>ExtWrite(1)<!>
    verify(t.a == 1)
}

fun <!VIPER_TEXT!>inPreconditionClient<!>() {
    val t: @Unique InPrecondition = InPrecondition(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>t.a == 1<!>)
}

// Facts from a verified law-free constructor.
<!VIPER_TEXT!>class Plain<!>(val a: Int, var b: Int) {
    val sum: Int = 3
}

fun <!VIPER_TEXT!>plainClient<!>() {
    val p: @Unique Plain = Plain(1, 2)
    verify(p.a == 1, p.b == 2, p.sum == 3)
    refute(false)
}

// A construction whose verification is skipped gives its clients no field facts, whichever is checked first.
fun <!VIPER_TEXT!>skippedEarlyClient<!>() {
    val s = SkippedConstruction(1, false)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.a == 1<!>)
}

class SkippedConstruction<!VERIFICATION_SKIPPED!>(val a: Int, flag: Boolean)<!> {
    var value: Int
    init {
        <!UNSUPPORTED_OWNERSHIP!>try {
            if (flag) throw IllegalArgumentException()
            value = 1
        } catch (error: IllegalArgumentException) {
            value = 2
        }<!>
    }
}

fun <!VIPER_TEXT!>skippedLateClient<!>() {
    val s = SkippedConstruction(1, false)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.a == 1<!>)
}

// A property initializer may read an own stored property that is already assigned.
<!VIPER_TEXT!>class SumOfParameters<!>(var a: Int, val b: Int) {
    var sum = a.toLong() + b.toLong()
}

fun <!VIPER_TEXT!>sumClient<!>() {
    val s: @Unique SumOfParameters = SumOfParameters(1, 2)
    verify(s.a == 1, s.b == 2)
    refute(false)
}
