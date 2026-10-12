// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface BorrowedView {
    @Pure @Unique
    fun <!VIPER_TEXT!>observe<!>(): Int

    @Unique @Borrowed
    fun <!VIPER_TEXT!>touch<!>()
}

class BorrowedCell(var value: Int) : BorrowedView {
    @Pure @Unique
    override fun <!VIPER_TEXT!>observe<!>(): Int = value

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>touch<!>() {
        value = 0
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>throughThis<!>() {
        (this as BorrowedView).touch()
        value = 7
        verify(value == 7)
        refute(false)
    }
}

@NeverConvert
fun lend(cell: @Unique @Borrowed BorrowedView, n: Int): Int {
    postconditions<Int> { result -> result == n + 1 }
    return n + 1
}

fun <!VIPER_TEXT!>borrowedParameter<!>(cell: @Unique @Borrowed BorrowedCell) {
    val result = lend(cell, 4)
    verify(result == 5)
    cell.value = 9
    verify(cell.value == 9)
    lend(cell, 10)
    refute(false)
}

fun <!VIPER_TEXT!>ownedLocal<!>() {
    val cell: @Unique BorrowedCell = BorrowedCell(1)
    val result = lend(cell, 5)
    verify(result == 6)
    cell.value = 9
    verify(cell.value == 9)
    refute(false)
}

fun <!VIPER_TEXT!>lostField<!>(cell: @Unique @Borrowed BorrowedCell) {
    cell.value = 2
    lend(cell, 6)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>cell.value == 2<!>)
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>inlineLend<!>(cell: @Unique @Borrowed BorrowedView) {
    cell.touch()
}

fun <!VIPER_TEXT!>inlineBorrow<!>(cell: @Unique @Borrowed BorrowedCell) {
    inlineLend(cell)
    cell.value = 9
    verify(cell.value == 9)
    lend(cell, 10)
    refute(false)
}

fun <!VIPER_TEXT!>inlineLostField<!>(cell: @Unique @Borrowed BorrowedCell) {
    cell.value = 2
    inlineLend(cell)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>cell.value == 2<!>)
}

fun <!VIPER_TEXT!>pureViewCall<!>(cell: @Unique @Borrowed BorrowedCell) {
    (cell as BorrowedView).observe()
    cell.value = 9
    verify(cell.value == 9)
    lend(cell, 10)
    refute(false)
}

@NeverConvert
fun nullableLend(cell: @Unique @Borrowed BorrowedView?) {}

fun <!VIPER_TEXT!>nullableBorrow<!>(cell: @Unique @Borrowed BorrowedCell?) {
    nullableLend(cell)
    if (cell != null) {
        cell.value = 9
        verify(cell.value == 9)
    }
    nullableLend(cell)
    refute(false)
}

fun <!VIPER_TEXT!>freshBorrow<!>() {
    val result = lend(BorrowedCell(1), 3)
    verify(result == 4)
    refute(false)
}

class CellHolder(val cell: @Unique BorrowedCell)

fun <!VIPER_TEXT!>inlineFieldBorrow<!>(holder: @Unique @Borrowed CellHolder) {
    inlineLend(holder.cell)
    holder.cell.value = 9
    verify(holder.cell.value == 9)
    lend(holder.cell, 10)
    refute(false)
}
