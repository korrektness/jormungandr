// FULL_JDK
// WITH_STDLIB



class CustomList(override val size: Int, value: Int) : AbstractList<Int>() {
    override fun <!VIPER_TEXT!>get<!>(index: Int): Int = value
    private val value = value
}

fun <!VIPER_TEXT!>test<!>(n: Int) {
    val customList = CustomList(n, 0)
    if (!customList.isEmpty()) {
        customList[customList.size - 1]
        customList[0]
    }
}
