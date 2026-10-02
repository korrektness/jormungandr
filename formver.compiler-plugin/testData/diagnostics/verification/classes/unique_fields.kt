import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

class UniquePrimitiveFields(
    val sharedVal: Int,
    var sharedVar: Int,
)

@AlwaysVerify
fun <!VIPER_TEXT!>testPrimitiveFieldGetterUnique<!>(pf: @Unique UniquePrimitiveFields) {
    val sharedVal = pf.sharedVal
    var sharedVar = pf.sharedVar
}

@AlwaysVerify
fun <!VIPER_TEXT!>testPrimitiveFieldGetterShared<!>(pf: UniquePrimitiveFields) {
    val sharedVal = pf.sharedVal
    var sharedVar = pf.sharedVar
}

@AlwaysVerify
fun <!VIPER_TEXT!>testPrimitiveFieldSetterUnique<!>(pf: @Unique UniquePrimitiveFields) {
    pf.sharedVar = 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>testPrimitiveFieldSetterShared<!>(pf: UniquePrimitiveFields) {
    pf.sharedVar = 3
}

class UniqueReferenceFields(
    val sharedVal: UniquePrimitiveFields,
    var sharedVar: UniquePrimitiveFields,
    val uniqueVal: @Unique UniquePrimitiveFields,
    var uniqueVar: @Unique UniquePrimitiveFields
)

@AlwaysVerify
fun <!VIPER_TEXT!>testReferenceFieldGetterUnique<!>(rf: @Unique UniqueReferenceFields) {
    val sharedVal = rf.sharedVal
    var sharedVar = rf.sharedVar
    val uniqueVal = rf.uniqueVal
    var uniqueVar = rf.uniqueVar
}

@AlwaysVerify
fun <!VIPER_TEXT!>testReferenceFieldGetterShared<!>(rf: UniqueReferenceFields) {
    val sharedVal = rf.sharedVal
    var sharedVar = rf.sharedVar
    val uniqueVal = rf.uniqueVal
    var uniqueVar = rf.uniqueVar
}

@AlwaysVerify
fun <!VIPER_TEXT!>testReferenceFieldSetterUnique<!>(rf: @Unique UniqueReferenceFields) {
    rf.sharedVar = UniquePrimitiveFields(5, 6)
    rf.uniqueVar = UniquePrimitiveFields(9, 10)
}

@AlwaysVerify
fun <!VIPER_TEXT!>testReferenceFieldSetterShared<!>(rf: UniqueReferenceFields) {
    rf.sharedVar = UniquePrimitiveFields(13, 14)
    rf.uniqueVar = UniquePrimitiveFields(17, 18)
}
