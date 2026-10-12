// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

interface ZeroLawBase {
    @Pure @Unique fun <!VIPER_TEXT!>observe<!>(): Int

    @SpecOf("advance") @Unique @Borrowed
    fun advanceSpec() {
        postconditions<Unit> { observe() == old(observe()) + 1 }
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>advance<!>()
}

interface ZeroLawLeft : ZeroLawBase
interface ZeroLawRight : ZeroLawBase
interface ZeroLawDiamond : ZeroLawLeft, ZeroLawRight

class ZeroLawCell(var value: Int) : ZeroLawDiamond {
    @Pure @Unique override fun <!VIPER_TEXT!>observe<!>(): Int = value

    @Unique @Borrowed override fun <!VIPER_TEXT!>advance<!>() {
        value++
    }
}

fun <!VIPER_TEXT!>advanceDiamond<!>(view: @Unique @Borrowed ZeroLawDiamond) {
    postconditions<Unit> { view.observe() == old(view.observe()) + 1 }
    view.advance()
}

fun <!VIPER_TEXT!>borrowedZeroLaws<!>(cell: @Unique @Borrowed ZeroLawCell) {
    val before = cell.value
    advanceDiamond(cell)
    verify(cell.value == before + 1)
    advanceDiamond(cell)
    verify(cell.value == before + 2)
    refute(false)
}

fun <!VIPER_TEXT!>permanentZeroLaws<!>(cell: @Unique ZeroLawCell) {
    val before = cell.value
    val diamond: @Unique ZeroLawDiamond = cell
    val base: @Unique ZeroLawBase = diamond
    base.advance()
    verify(base.observe() == before + 1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>base.observe() == before<!>)
}
