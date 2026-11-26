// FULL_JDK
// DIAGNOSTIC_KINDS

import org.jetbrains.kotlin.formver.plugin.*

abstract class Checked {
    val sum: Int

    constructor(first: Int, second: Int) {
        preconditions { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first == 1<!>; second == 2 }
        sum = first + second
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <!VIPER_TEXT!>makeChecked<!>(): Checked = object : Checked(1, 2) {}

fun <!VIPER_TEXT!>anonymousConstruction<!>() {
    var count = 0
    val first: Checked = object : Checked(second = run { count += 1; 2 }, first = run { val seen = count; count += 1; seen }) {}
    val second = makeChecked()
    val third = makeChecked()
    verify(first.sum == 3)
    verify(count == 2)
    verify(first !== second)
    verify(second !== third)
}

fun <!VIPER_TEXT!>anonymousSuperArgumentThrows<!>(fail: Boolean) {
    var count = 0
    try {
        val value: Checked = object : Checked(if (fail) throw IllegalArgumentException() else 1, run { count += 1; 2 }) {}
        verify(value.sum == 3)
        verify(count == 1)
    } catch (e: IllegalArgumentException) {
        verify(count == 0)
    }
}

fun <!VIPER_TEXT!>invalidAnonymousSuper<!>(): Checked = object : Checked(0, 2) {}

fun <!VERIFICATION_SKIPPED!>anonymousMember<!>(): Any = <!UNSUPPORTED_FEATURE!>object<!> {
    fun <!VIPER_TEXT!>member<!>(): Int = 0
}

fun <!VERIFICATION_SKIPPED!>anonymousCapture<!>(n: Int): Any = <!UNSUPPORTED_FEATURE!>object<!> {
    <!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>member<!>(): Int = n<!>
}

open <!VIPER_TEXT!>class NestedOther<!>

open <!VIPER_TEXT!>class NestedBase<!> {
    private val hidden = object : NestedOther() {}
}

fun <!VIPER_TEXT!>nestedNominalTypes<!>() {
    val value: Any = object : NestedBase() {}
    verify(value is NestedBase)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>value is NestedOther<!>)
}

open <!VIPER_TEXT!>class OwnedBase<!>(val data: @Unique IntArray)

private fun <!VERIFICATION_SKIPPED!>unsupportedOwned<!>() = <!UNSUPPORTED_FEATURE!>object : OwnedBase(IntArray(1))<!> {}

fun <!VIPER_TEXT!>useUnsupportedOwned<!>(): OwnedBase = unsupportedOwned()

interface AnonymousObservation {
    @Pure fun <!VIPER_TEXT!>observed<!>(): Int
}

open <!VIPER_TEXT!>class ObserverBase<!> : AnonymousObservation {
    @Pure final override fun <!VIPER_TEXT!>observed<!>(): Int = 1
}

fun <!VERIFICATION_SKIPPED!>unsupportedObserverFamily<!>(): ObserverBase = <!UNSUPPORTED_FEATURE!>object : ObserverBase()<!> {}

fun interface AnonymousAction {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

open <!VIPER_TEXT!>class CallbackBase<!> {
    val action: @Unique AnonymousAction = AnonymousAction {
        exposes(AnonymousAction::class) { state() == 0 }
        1
    }
}

fun <!VERIFICATION_SKIPPED!>unsupportedSuperclassCallback<!>(): CallbackBase = <!UNSUPPORTED_FEATURE!>object : CallbackBase()<!> {}

interface PositiveResult {
    @SpecOf("get")
    fun getSpec() { postconditions<Int> { it > 0 } }
    fun <!VIPER_TEXT!>get<!>(): Int
}

open <!VIPER_TEXT!>class ZeroResult<!> { open fun <!VIPER_TEXT!>get<!>(): Int = 0 }
abstract class AbstractResult : ZeroResult(), PositiveResult
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class NamedResult<!> : AbstractResult()<!>

fun <!VERIFICATION_SKIPPED!>inheritedContractCounterexample<!>() {
    val result: PositiveResult = <!UNSUPPORTED_FEATURE!>object : AbstractResult()<!> {}
    val value = result.get()
    verify(value > 0)
}
