

<!VIPER_TEXT!>class PrimitiveFields<!>(val a: Int, val b: Int)

fun <!VIPER_TEXT!>createPrimitiveFields<!>(): PrimitiveFields = PrimitiveFields(10, 20)

<!VIPER_TEXT!>class Recursive<!>(val a: Recursive?)

fun <!VIPER_TEXT!>createRecursive<!>(): Recursive = Recursive(null)

<!VIPER_TEXT!>class FieldInBody<!>(val c: Int) {
    val a = 5
}

fun <!VIPER_TEXT!>createFieldInBody<!>(): FieldInBody = FieldInBody(10)
