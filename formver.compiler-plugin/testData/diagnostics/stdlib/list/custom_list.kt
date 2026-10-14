// FULL_JDK
// WITH_STDLIB



<!INVALID_EXTERNAL_SPEC!><!VIPER_TEXT!>class CustomList<!>(override val size: Int, value: Int) : AbstractList<Int>() {
    override fun <!VIPER_TEXT!>get<!>(index: Int): Int = value
    private val value = value
}<!>

fun <!VIPER_TEXT!>test<!>(n: Int) {
    val customList = CustomList(n, 0)
    if (!customList.isEmpty()) {
        customList[<!INTEGER_OVERFLOW!>customList.size - 1<!>]
        customList[0]
    }
}
