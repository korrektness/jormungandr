// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

interface GenericView<T> {
    @Pure @Unique fun <!VIPER_TEXT!>value<!>(): T
    @Pure @Unique fun <!VIPER_TEXT!>same<!>(candidate: T): Boolean
}

class GenericCell<T>(val contents: T) : GenericView<T> {
    @Pure @Unique override fun <!VIPER_TEXT!>value<!>(): T = contents
    @Pure @Unique override fun <!VIPER_TEXT!>same<!>(candidate: T): Boolean = candidate == contents
}

fun <!VIPER_TEXT!>genericBooleanView<!>() {
    val cell: @Unique GenericCell<Boolean> = GenericCell(true)
    val view: @Unique GenericView<Boolean> = cell
    verify(view.value())
    verify(view.same(true))
    verify(!view.same(false))
    refute(false)
}

class BooleanView(val contents: Boolean) : GenericView<Boolean> {
    @Pure @Unique override fun <!VIPER_TEXT!>value<!>(): Boolean = contents
    @Pure @Unique override fun <!VIPER_TEXT!>same<!>(candidate: Boolean): Boolean = candidate == contents
}

fun <!VIPER_TEXT!>specializedImplementation<!>() {
    val cell: @Unique BooleanView = BooleanView(true)
    val view: @Unique GenericView<Boolean> = cell
    verify(view.value())
    verify(view.same(true))
    verify(!view.same(false))
    refute(false)
}

fun <!VIPER_TEXT!>lendGeneric<!>(view: @Unique @Borrowed GenericView<Boolean>): Boolean {
    postconditions<Boolean> { result ->
        result == old(view.value())
        view.value() == old(view.value())
        forAll<Boolean> { b -> view.same(b) == old(view.same(b)) }
    }
    return view.value()
}

fun <!VIPER_TEXT!>borrowGenericBinding<!>() {
    val cell: @Unique GenericCell<Boolean> = GenericCell(true)
    val value = lendGeneric(cell)
    verify(value)
    verify(cell.value())
    refute(false)
}

class GenericToken

fun <!VIPER_TEXT!>genericNullableView<!>(token: GenericToken?) {
    val cell: @Unique GenericCell<GenericToken?> = GenericCell(token)
    val view: @Unique GenericView<GenericToken?> = cell
    verify(view.value() == token)
    verify(view.same(token))
    refute(false)
}

fun <!VIPER_TEXT!>genericFalseClaim<!>() {
    val cell: @Unique GenericCell<Boolean> = GenericCell(true)
    val view: @Unique GenericView<Boolean> = cell
    verify(<!VIPER_VERIFICATION_ERROR!>view.same(false)<!>)
}
