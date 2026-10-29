// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class GenericEcho<!><E> {
    fun <!VIPER_TEXT!>echo<!>(value: E): E {
        postconditions<E> { r -> r === value }
        return value
    }

    fun <!VIPER_TEXT!>nonNull<!>(value: E): E {
        preconditions { value !== null }
        postconditions<E> { r -> r === value }
        return value
    }
}

fun <!VIPER_TEXT!>lostContract<!>(g: GenericEcho<Any?>, value: Any?) {
    val returned = g.echo(value)
    verify(returned === value)
}

fun <!VIPER_TEXT!>twoInstantiations<!>(strings: GenericEcho<String>, ints: GenericEcho<Int>, text: String, number: Int) {
    val returnedText = strings.echo(text)
    val returnedNumber = ints.echo(number)
    verify(returnedText === text)
    verify(returnedNumber == number)
}

fun <!VIPER_TEXT!>falseIdentity<!>(g: GenericEcho<Any?>, value: Any?) {
    val returned = g.echo(value)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>returned !== value<!>)
}

fun <!VIPER_TEXT!>validPrecondition<!>(g: GenericEcho<Any?>, value: Any) {
    val returned = g.nonNull(value)
    verify(returned === value)
}

fun <!VIPER_TEXT!>invalidPrecondition<!>(g: GenericEcho<Any?>) {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>g.nonNull(null)<!>
}

<!VIPER_TEXT!>class SpecEcho<!><E> {
    @SpecOf("echo")
    fun echoSpec(value: E) {
        postconditions<E> { r -> r === value }
    }

    fun <!VIPER_TEXT!>echo<!>(value: E): E = value
}

fun <!VIPER_TEXT!>siblingSpecification<!>(g: SpecEcho<Any?>, value: Any?) {
    val returned = g.echo(value)
    verify(returned === value)
}

interface BoxView {
    @Pure @Unique fun <!VIPER_TEXT!>size<!>(): Int
    @SpecOf("elementAt") @Unique fun elementAtSpec(i: Int) {
        preconditions { 0 <= i && i < size() }
    }
    @Pure @Unique fun <!VIPER_TEXT!>elementAt<!>(i: Int): Any?
}

<!VIPER_TEXT!>class Box<!><E>(private val data: @Unique Array<Any?>) : BoxView {
    @Pure @Unique override fun <!VIPER_TEXT!>size<!>(): Int = data.size

    @Pure @Unique override fun <!VIPER_TEXT!>elementAt<!>(i: Int): Any? {
        preconditions { 0 <= i && i < size() }
        return data[i]
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>get<!>(i: Int): Any? {
        preconditions { 0 <= i && i < size() }
        postconditions<Any?> { r ->
            r === elementAt(i)
            size() == old(size())
            forAll<Int> { j -> (0 <= j && j < size()) implies (elementAt(j) === old(elementAt(j))) }
        }
        return data[i]
    }
}

fun <!VIPER_TEXT!>borrowTwice<!>(box: @Unique @Borrowed Box<Int>, i: Int) {
    preconditions { 0 <= i && i < box.size() }
    val first = box.get(i)
    verify(first === box.elementAt(i))
    val second = box.get(i)
    verify(second === first)
}

fun <!VIPER_TEXT!>wrongIndex<!>(box: @Unique @Borrowed Box<Int>) {
    preconditions { box.size() >= 2 }
    val returned = box.get(0)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>returned === box.elementAt(1)<!>)
}
