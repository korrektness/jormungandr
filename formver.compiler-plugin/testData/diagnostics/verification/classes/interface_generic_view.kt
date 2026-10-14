// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

interface GenericView<T> {
    @Pure @Unique fun <!VIPER_TEXT!>value<!>(): T
    @Pure @Unique fun <!VIPER_TEXT!>same<!>(candidate: T): Boolean
}

enum class Flag { ON, OFF }

<!VIPER_TEXT!>class GenericCell<!><T : Flag?>(val contents: T) : GenericView<T> {
    @Pure @Unique override fun <!VIPER_TEXT!>value<!>(): T = contents
    @Pure @Unique override fun <!VIPER_TEXT!>same<!>(candidate: T): Boolean = candidate == contents
}

fun <!VIPER_TEXT!>genericEnumView<!>(other: Flag) {
    val cell: @Unique GenericCell<Flag> = GenericCell(Flag.ON)
    val view: @Unique GenericView<Flag> = cell
    verify(view.value() == Flag.ON)
    verify(view.same(Flag.ON))
    verify(view.same(other) == (other === Flag.ON))
    refute(false)
}

<!VIPER_TEXT!>class BooleanView<!>(val contents: Boolean) : GenericView<Boolean> {
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

fun <!VIPER_TEXT!>lendGeneric<!>(view: @Unique @Borrowed GenericView<Flag>): Flag {
    postconditions<Flag> { result ->
        result == old(view.value())
        view.value() == old(view.value())
        forAll<Flag> { f -> view.same(f) == old(view.same(f)) }
    }
    return view.value()
}

fun <!VIPER_TEXT!>borrowGenericBinding<!>() {
    val cell: @Unique GenericCell<Flag> = GenericCell(Flag.ON)
    val value = lendGeneric(cell)
    verify(value == Flag.ON)
    verify(cell.value() == Flag.ON)
    refute(false)
}

fun <!VIPER_TEXT!>genericNullableView<!>(token: Flag?) {
    val cell: @Unique GenericCell<Flag?> = GenericCell(token)
    val view: @Unique GenericView<Flag?> = cell
    verify(view.value() == token)
    verify(view.same(token))
    refute(false)
}

fun <!VIPER_TEXT!>genericFalseClaim<!>() {
    val cell: @Unique GenericCell<Flag> = GenericCell(Flag.ON)
    val view: @Unique GenericView<Flag> = cell
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>!view.same(Flag.ON)<!>)
}
