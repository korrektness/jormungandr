// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

fun interface CounterSpec {
    fun invoke(): Int
    @GhostObserver @Pure fun value(): Int = ghostObserver()
    @GhostObserver @Pure fun step(): Int = ghostObserver()
    @Law @Pure fun nonnegativeStep(): Boolean = step() >= 0
}

fun interface OtherSpec {
    fun invoke(): Int
    @GhostObserver @Pure fun value(): Int = ghostObserver()
}

fun accepted(start: Int): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == start; step() == 1 }
    start + 1
}

fun namedBindings(): CounterSpec = CounterSpec {
    exposes(body = { value() == 0; step() == 1 }, spec = CounterSpec::class)
    0
}

fun takesSpec(counter: CounterSpec) = Unit

fun implicitConversion(start: Int) {
    takesSpec {
        exposes(CounterSpec::class) { value() == start; step() == 2 }
        start + 2
    }
}

fun implicitMissing() { takesSpec <!INVALID_EXPOSES!>{ 0 }<!> }

fun interface DerivedSpec : CounterSpec

fun inherited(): DerivedSpec = DerivedSpec {
    exposes(DerivedSpec::class) { value() == 0; step() == 1 }
    0
}

fun inheritedMissing(): DerivedSpec = DerivedSpec {
    <!INVALID_EXPOSES!>exposes(DerivedSpec::class) { value() == 0 }<!>
    0
}

fun repeated(): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == 0; step() == 1 }
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1 }<!>
    0
}

fun foreignReceiver(other: CounterSpec): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { other.value() == 0; step() == 1 }<!>
    0
}

fun extraStatement(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1; true }<!>
    0
}

fun nested(): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == 0; step() == 1 }
    val f = {
        <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1 }<!>
        0
    }
    f()
}

fun missing(): CounterSpec = <!INVALID_EXPOSES!>CounterSpec { 0 }<!>

fun duplicate(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; value() == 1; step() == 1 }<!>
    0
}

fun incomplete(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0 }<!>
    0
}

fun late(): CounterSpec = <!INVALID_EXPOSES!>CounterSpec {
    val x = 0
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == x; step() == 1 }<!>
    x
}<!>

fun wrongSpec(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(OtherSpec::class) { value() == 0 }<!>
    0
}

fun wrongOperator(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() != 0; step() == 1 }<!>
    0
}

fun ordinaryLambda(): () -> Int = {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1 }<!>
    0
}

fun outside() {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1 }<!>
}

class State(var n: Int)

fun borrowedCapture(state: @Unique @Borrowed State): CounterSpec = CounterSpec <!INVALID_UNIQUENESS_CAPTURE, LOCALITY_MISMATCH!>{
    exposes(CounterSpec::class) { value() == state.n; step() == 1 }
    0
}<!>

fun interface OwnedSpec {
    @Unique @Borrowed fun invoke(): Int
    @GhostObserver @Pure @Unique fun value(): Int = ghostObserver()
}

fun ownedSpec(): OwnedSpec = OwnedSpec {
    exposes(OwnedSpec::class) { value() == 0 }
    0
}

fun ghostInSpecifications(counter: CounterSpec) {
    preconditions { counter.value() >= 0 }
    postconditions<Unit> { counter.step() >= 0 }
    verify(counter.value() == 0)
    refute(counter.value() < 0)
}

<!INVALID_LAW!>@Law @Pure fun topLevelLaw(counter: CounterSpec): Boolean = <!INVALID_LAW, INVALID_OBSERVER!><!INVALID_LAW!>counter<!>.value()<!> >= 0<!>

class ClassGhostLaw(val counter: CounterSpec) {
    @Law @Pure fun law(): Boolean = <!INVALID_LAW, INVALID_OBSERVER!>counter.value()<!> >= 0
}

fun interface ForeignGhostLaw : CounterSpec {
    <!INVALID_LAW!>@Law @Pure fun foreign(counter: CounterSpec): Boolean = <!INVALID_LAW, INVALID_OBSERVER!><!INVALID_LAW!>counter<!>.value()<!> >= 0<!>
}

fun runtimeDerived(counter: DerivedSpec): Int = <!INVALID_OBSERVER!>counter.step()<!>

fun runtimeReference(counter: CounterSpec): () -> Int = <!INVALID_OBSERVER!>counter::value<!>

fun dynamicToken(other: CounterSpec): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(other::class) { value() == 0; step() == 1 }<!>
    0
}

fun nonLiteralBindings(body: CounterSpec.() -> Unit): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class, body)<!>
    0
}

fun emptyBindings(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) {}<!>
    0
}

fun returningBindings(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; step() == 1; return@exposes }<!>
    0
}

fun returningEquality(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == 0; return@exposes <!RETURN_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>step() == 1<!> }<!>
    0
}

fun nestedReturnBinding(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == if (true) { return@exposes } else 0; step() == 1 }<!>
    0
}

fun effectfulValue(): Int = 0
@Pure fun pureValue(x: Int): Int = x + 1

fun impureBinding(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == effectfulValue(); step() == 1 }<!>
    0
}

fun nestedImpureBinding(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == pureValue(effectfulValue()) + 1; step() == 1 }<!>
    0
}

class EffectfulGetter {
    val value: Int get() = effectfulValue()
}

fun impureGetterBinding(state: EffectfulGetter): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == state.value; step() == 1 }<!>
    0
}

fun assignmentBinding(state: State): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == if (true) { state.n = 1; 0 } else 0; step() == 1 }<!>
    0
}

fun throwingBinding(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == (throw IllegalStateException()); step() == 1 }<!>
    0
}

class CustomEquals {
    override fun equals(other: Any?): Boolean = other is CustomEquals
    override fun hashCode(): Int = 0
}

fun userEqualityBinding(a: CustomEquals, b: CustomEquals): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == if (a == b) 1 else 0; step() == 1 }<!>
    0
}

fun castBinding(x: Any): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == x as Int; step() == 1 }<!>
    0
}

fun notNullBinding(x: Int?): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == x!!; step() == 1 }<!>
    0
}

fun templateBinding(start: Int): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == "$start".length; step() == 1 }<!>
    0
}

fun selfReferenceBinding(): CounterSpec = CounterSpec {
    <!INVALID_EXPOSES!>exposes(CounterSpec::class) { value() == step(); step() == 1 }<!>
    0
}

fun nullCheckBinding(a: CustomEquals?, x: Any): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == if (a == null && x is Int) 1 else 0; step() == (x as? Int ?: 1) }
    0
}

fun pureBinding(start: Int): CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == pureValue(start) + 2; step() == if (start > 0) 1 else 0 }
    start
}

fun runtimeUse(counter: CounterSpec): Int = <!INVALID_OBSERVER!>counter.value()<!>
fun placeholderUse(): Int = <!INVALID_OBSERVER!>ghostObserver()<!>
fun placeholderReference(): () -> Int = <!INVALID_OBSERVER!>::ghostObserver<!>

class OverrideGhost : CounterSpec {
    override fun invoke(): Int = 0
    <!INVALID_OBSERVER, INVALID_OBSERVER!>@Pure override fun value(): Int = 0<!>
}

open class OpenGhostOverride : CounterSpec {
    override fun invoke(): Int = 0
    <!INVALID_OBSERVER, INVALID_OBSERVER!>@Pure override fun value(): Int = 0<!>
}

class PlainGhostOverride : CounterSpec {
    override fun invoke(): Int = 0
    <!INVALID_OBSERVER!>override fun value(): Int = 0<!>
}

class AnnotatedGhostOverride : CounterSpec {
    override fun invoke(): Int = 0
    <!INVALID_OBSERVER, INVALID_OBSERVER!>@GhostObserver override fun value(): Int = 1<!>
}

fun interface SubInterfaceGhostOverride : CounterSpec {
    <!INVALID_OBSERVER, INVALID_OBSERVER, INVALID_OBSERVER!>@GhostObserver @Pure override fun value(): Int = 1<!>
}

<!INVALID_LAW, INVALID_OBSERVER, INVALID_OBSERVER!>class DelegatedGhostOverride(counter: CounterSpec) : CounterSpec by counter<!>

open class GhostNameCollision {
    fun value(): Int = 0
}

<!INVALID_OBSERVER!><!MANY_IMPL_MEMBER_NOT_IMPLEMENTED!>class InheritedGhostOverride<!> : GhostNameCollision(), CounterSpec {
    override fun invoke(): Int = 0
}<!>

interface OrdinaryInterface {
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun value(): Int = ghostObserver()<!>
}

<!INVALID_OBSERVER!>@GhostObserver @Pure fun topLevelGhost(): Int = ghostObserver()<!>

fun interface BadGhost {
    fun invoke(): Int
    <!INVALID_OBSERVER!>@GhostObserver fun value(): Int = ghostObserver()<!>
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun step(): Int = 1<!>
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun indexed(i: Int): Int = ghostObserver()<!>
}
