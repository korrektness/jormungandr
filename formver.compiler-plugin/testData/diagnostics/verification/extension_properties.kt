// FULL_JDK



val Int.intValProp: Int <!VIPER_TEXT!>get<!>() = this

var Int.intVarProp: Int
    <!VIPER_TEXT!>get<!>() = 0
    <!VIPER_TEXT!>set<!>(v) {}

fun <!VIPER_TEXT!>extensionGetterProperty<!>() {
    val a = 0.intValProp
    val b = 1.intValProp.intValProp
}

fun <!VIPER_TEXT!>extensionSetterProperty<!>() {
    42.intVarProp = 0
}

class PrimitiveField(val x: Int)

val PrimitiveField.pfValProp: Int <!VIPER_TEXT!>get<!>() = this.x

var PrimitiveField.pfVarProp: Int
    <!VIPER_TEXT!>get<!>() = 0
    <!VIPER_TEXT!>set<!>(v) {}

fun <!VIPER_TEXT!>extensionGetterPropertyUserDefinedClass<!>(pf: PrimitiveField) {
    val x = pf.pfValProp
}

fun <!VIPER_TEXT!>extensionSetterPropertyUserDefinedClass<!>(pf: PrimitiveField) {
    pf.pfVarProp = 42
}
