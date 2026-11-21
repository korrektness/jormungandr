// WITH_STDLIB

interface Box<T> {
    val value: T
}

fun <!VIPER_TEXT!>starValue<!>(b: Box<*>): Any? = b.value
