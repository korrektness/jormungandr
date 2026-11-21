// WITH_STDLIB

fun <!VIPER_TEXT!>localInvoke<!>(f: (Int) -> Int?): Int? = f(1)?.let { it }
