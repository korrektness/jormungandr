import org.jetbrains.kotlin.formver.plugin.AlwaysVerify


class PrimitiveFields(val a: Int, val b: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>createPrimitiveFields<!>(): PrimitiveFields = PrimitiveFields(10, 20)

class Recursive(val a: Recursive?)

@AlwaysVerify
fun <!VIPER_TEXT!>createRecursive<!>(): Recursive = Recursive(null)

class FieldInBody(val c: Int) {
    val a = 5
}

@AlwaysVerify
fun <!VIPER_TEXT!>createFieldInBody<!>(): FieldInBody = FieldInBody(10)
