// FULL_JDK
// DIAGNOSTIC_KINDS
import java.util.Comparator
import java.util.Optional
import java.util.function.Consumer
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>comparator<!>(): Comparator<Int> = Comparator { left, right -> <!INTEGER_OVERFLOW!>left - right<!> }

fun <!VERIFICATION_SKIPPED!>referenceComparator<!>(): Comparator<Int> = <!UNSUPPORTED_FEATURE!>Comparator(Int::compareTo)<!>

fun <!VIPER_TEXT!>retain<!>(action: Consumer<Int>): Consumer<Int> = action

fun <!VIPER_TEXT!>callbackMayThrow<!>() {
    throwsOnlyIf { false }
    val action = Consumer<Int> { error("callback") }
    <!OPAQUE_CALL, THROWS_CONDITION_NOT_ESTABLISHED!>action.accept(1)<!>
}

fun <!VIPER_TEXT!>sharedAfterConversion<!>(value: Optional<Int>) {
    var x = 0
    <!OPAQUE_CALL!>value.ifPresent { x = <!INTEGER_OVERFLOW!>it + 1<!> }<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 0<!>)
}

fun interface ReceiverAction {
    fun Int.<!VIPER_TEXT!>run<!>(other: Int): Int
}

fun Int.<!VIPER_TEXT!>receiverAction<!>(): ReceiverAction = ReceiverAction { other ->
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>this == this@receiverAction<!>)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>this == other<!>)
    this
}

<!VIPER_TEXT!>class SamBox<!>(var n: Int)
fun <!VIPER_TEXT!>ownedBox<!>(): @Unique SamBox = SamBox(0)
fun <!VIPER_TEXT!>consumeBox<!>(box: @Unique SamBox) {}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>cannotConsumeCapture<!>() {
    val box = ownedBox()
    retain { box.n = it }
    consumeBox(<!UNIQUENESS_MISMATCH!>box<!>)
}

interface LowerBound {
    @SpecOf("result") fun lowerBound() { postconditions<Int> { it >= 0 } }
    fun <!VIPER_TEXT!>result<!>(): Int
}

interface UpperBound {
    @SpecOf("result") fun upperBound() { postconditions<Int> { it <= 0 } }
    fun <!VIPER_TEXT!>result<!>(): Int
}

fun interface BothBounds : LowerBound, UpperBound {
    override fun <!OVERRIDE_NOT_REFINING, OVERRIDE_NOT_REFINING, VIPER_TEXT!>result<!>(): Int
}

fun <!VERIFICATION_SKIPPED!>rejectInheritedContracts<!>(): BothBounds = <!UNSUPPORTED_FEATURE!>BothBounds { 1 }<!>

fun interface OwningAction {
    fun <!VIPER_TEXT!>use<!>(box: @Unique SamBox)
}

fun <!VERIFICATION_SKIPPED!>unsupportedOwnershipContract<!>(): OwningAction = <!UNSUPPORTED_FEATURE!>OwningAction { }<!>

open <!VIPER_TEXT!>class SamSuperBase<!> {
    fun <!VIPER_TEXT!>answer<!>(): Int {
        postconditions<Int> { it == 42 }
        return 42
    }
}

fun <!VIPER_TEXT!>localSuperReceiver<!>(): Consumer<Int> = Consumer {
    <!VIPER_TEXT!>class Local<!> : SamSuperBase() {
        fun <!VIPER_TEXT!>check<!>() {
            val result = super.answer()
            verify(result == 42)
        }
    }
    Local().check()
}

<!VIPER_TEXT!>class SamSuperCapture<!> : SamSuperBase() {
    fun <!VERIFICATION_SKIPPED!>capture<!>(): Consumer<Int> = <!UNSUPPORTED_FEATURE!>Consumer { super.answer() }<!>
}

<!UNSUPPORTED_FEATURE!>class SamPropertyInitializer {
    val action = Consumer<Int> { verify(false) }
}<!>

<!VERIFICATION_SKIPPED!>class SamInitBlock<!> {
    init {
        retain <!UNSUPPORTED_FEATURE!>{ verify(false) }<!>
    }
}
