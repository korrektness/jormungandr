// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.verify

<!VIPER_TEXT!>class Header<!>(override val key: String, override val value: String) : Map.Entry<String, String>

<!VIPER_TEXT!>class Names<!>(private val first: String) : Collection<String> {
    @Pure override val size: Int
        <!VIPER_TEXT!>get<!>() = 1

    override fun <!VIPER_TEXT!>isEmpty<!>(): Boolean {
        postconditions<Boolean> { result -> result }
        return true
    }

    override fun <!VIPER_TEXT!>contains<!>(element: String): Boolean = element == first

    override fun <!VIPER_TEXT!>containsAll<!>(elements: Collection<String>): Boolean = false

    override fun <!VIPER_TEXT!>iterator<!>(): Iterator<String> = throw NoSuchElementException()
}

fun <!VIPER_TEXT!>`own override is called with its own contract`<!>() {
    val names = Names("a")
    val empty = names.isEmpty()
    verify(empty)
}

fun <!VIPER_TEXT!>`model member on the object is the unchecked library call`<!>() {
    val names: Collection<String> = Names("a")
    val empty = <!UNCHECKED_LIBRARY_CALL!>names.isEmpty()<!>
    val size = <!UNCHECKED_LIBRARY_CALL!>names.size<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>empty == (size == 0)<!>)
}

fun <!VIPER_TEXT!>`entry implementation is read as fixed`<!>() {
    val entry: Map.Entry<String, String> = Header("Accept", "text")
    val first = entry.key
    val second = entry.key
    verify(first == second)
}

<!VIPER_TEXT!>class Forever<!> : Iterator<Int> {
    override fun <!VIPER_TEXT!>hasNext<!>(): Boolean = true
    override fun <!VIPER_TEXT!>next<!>(): Int = 0
}

<!VIPER_TEXT!>class Bag<!> {
    operator fun <!VIPER_TEXT!>iterator<!>(): Iterator<Int> = Forever()
}

<!VIPER_TEXT!>class Headers<!>(private val inner: Map<String, String>) : Map<String, String> by inner

fun <!VIPER_TEXT!>`delegated implementation constructs a shared object`<!>(inner: Map<String, String>) {
    val headers: Map<String, String> = Headers(inner)
    val size = <!UNCHECKED_LIBRARY_CALL!>headers.size<!>
}
