// WITH_STDLIB

<!VIPER_TEXT!>class Holder<!>(val items: List<String>)

fun <!VIPER_TEXT!>sharedField<!>(h: Holder): Int {
    var n = 0
    for (s in <!UNCHECKED_LIBRARY_CALL!>h.items<!>) if (s.isEmpty()) n = 1
    return n
}

fun <!VIPER_TEXT!>sharedParameter<!>(l: List<String>): Int {
    var n = 0
    for (s in <!UNCHECKED_LIBRARY_CALL!>l<!>) if (s.isEmpty()) n = 1
    return n
}

fun <!VIPER_TEXT!>mutatedThroughAlias<!>(view: List<Int>, list: MutableList<Int>) {
    for (x in <!UNCHECKED_LIBRARY_CALL!>view<!>) <!UNCHECKED_LIBRARY_CALL!>list.add(x)<!>
}

fun <!VIPER_TEXT!>ownedList<!>(): Int {
    val l = listOf("a")
    var n = 0
    for (s in l) if (s.isEmpty()) n = 1
    return n
}
