// RENDER_PREDICATES
// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class BindingArgument(var number: Int)

interface BindingView {
    @Pure @Unique
    fun <!VIPER_TEXT!>value<!>(): Int

    @Pure @Unique
    fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int

    @SpecOf("scaled") @Unique
    fun scaledSpec(factor: Int) {
        preconditions { -715827882 <= value() && value() <= 715827882 && 0 <= factor && factor <= 3 }
    }

    @Pure @Unique
    fun <!VIPER_TEXT!>read<!>(argument: @Unique BindingArgument): Int

    @SpecOf("read") @Unique
    fun readSpec(argument: @Unique @Borrowed BindingArgument) {
        preconditions { -2147483648L <= value().toLong() + argument.number.toLong() && value().toLong() + argument.number.toLong() <= 2147483647L }
    }
}

open class BindingCell(var contents: Int) : BindingView {
    @Pure @Unique
    final override fun <!VIPER_TEXT!>value<!>(): Int = contents

    @Pure @Unique
    final override fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int = contents * factor

    @Pure @Unique
    final override fun <!VIPER_TEXT!>read<!>(argument: @Unique BindingArgument): Int = contents + argument.number
}

class InheritedBindingCell(contents: Int) : BindingCell(contents)

fun <!VIPER_TEXT!>inheritedBinding<!>() {
    val cell: @Unique InheritedBindingCell = InheritedBindingCell(1)
    cell.contents = 6
    verify((cell as BindingView).scaled(2) == 12)
    verify(cell.contents == 6)
    val view: @Unique BindingView = cell
    verify(view.value() == 6)
    verify(view.scaled(3) == 18)
    refute(false)
}

fun <!VIPER_TEXT!>moveBinding<!>() {
    val cell: @Unique BindingCell = BindingCell(1)
    cell.contents = 7
    val argument: @Unique BindingArgument = BindingArgument(5)
    val view: @Unique BindingView = cell
    verify(view.value() == 7)
    verify(view.scaled(3) == 21)
    verify(view.read(argument) == 12)
    refute(false)
}

fun <!VIPER_TEXT!>borrowBinding<!>() {
    val cell: @Unique BindingCell = BindingCell(1)
    cell.contents = 9
    verify((cell as BindingView).value() == 9)
    verify((cell as BindingView).scaled(2) == 18)
    verify(cell.contents == 9)
    refute(false)
}

@NeverConvert
fun readBinding(view: @Unique @Borrowed BindingView): Int {
    preconditions {
        -715827882 <= view.value() && view.value() <= 715827882
        -2147483648L <= view.value().toLong() + view.scaled(2).toLong() && view.value().toLong() + view.scaled(2).toLong() <= 2147483647L
    }
    postconditions<Int> { result ->
        result == old(view.value()) + old(view.scaled(2))
        view.value() == old(view.value())
    }
    return view.value() + view.scaled(2)
}

fun <!VIPER_TEXT!>borrowedCallBinding<!>() {
    val cell: @Unique BindingCell = BindingCell(1)
    cell.contents = 4
    val result = readBinding(cell)
    verify(result == 12)
    verify(cell.contents == 4)
    refute(false)
}

fun <!VIPER_TEXT!>bareInterface<!>(view: @Unique @Borrowed BindingView) {
    val cell: @Unique BindingCell = BindingCell(7)
    val known: @Unique BindingView = cell
    verify(known.value() == 7)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>view.value() == 7<!>)
}
