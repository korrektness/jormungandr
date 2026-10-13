// NEVER_VALIDATE

<!VIPER_TEXT!>class PrimitiveProperty<!> {
    val nProp: Int
        <!VIPER_TEXT!>get<!>() {
            return 0
        }

    var aProp: Int = 0
        <!VIPER_TEXT!>set<!>(v) {}
}

fun <!VIPER_TEXT!>testPrimitivePropertyGetter<!>(pp: PrimitiveProperty) : Int = pp.nProp

fun <!VIPER_TEXT!>testPrimitivePropertySetter<!>(pp: PrimitiveProperty) {
    pp.aProp = 0
}

<!VIPER_TEXT!>class ReferenceProperty<!> {
    val rProp: PrimitiveProperty
        <!VIPER_TEXT!>get<!>() {
            return PrimitiveProperty()
        }

    var ppProp: PrimitiveProperty = PrimitiveProperty()
        <!VIPER_TEXT!>set<!>(v) {}
}

fun <!VIPER_TEXT!>testReferencePropertyGetter<!>(rp: ReferenceProperty) {
    val pp = rp.rProp
    val ppn = pp.nProp
}

fun <!VIPER_TEXT!>testCascadingPropertyGetter<!>(rp: ReferenceProperty) {
    val ppn = rp.rProp.nProp
}

fun <!VIPER_TEXT!>testReferencePropertySetter<!>(rp: ReferenceProperty) {
    rp.ppProp = PrimitiveProperty()
}
