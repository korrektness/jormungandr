// FULL_JDK
import kotlin.contracts.contract
import kotlin.contracts.ExperimentalContracts
import org.jetbrains.kotlin.formver.plugin.verify

<!VIPER_TEXT!>class Z<!>
<!VIPER_TEXT!>class Y<!> {
    val z = Z()
}

<!VIPER_TEXT!>class X<!> {
    val y = Y()
}

fun <!VIPER_TEXT!>cascadeGet<!>(x: X): Z {
    return x.y.z
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>receiverNotNullProved<!>(x: X?): Boolean {
    contract {
        returns(true) implies (x != null)
    }
    return x?.y != null
}

<!VIPER_TEXT!>class NullableY<!> {
    val z: Z? = Z()
}

<!VIPER_TEXT!>class NullableX<!> {
    val y: NullableY? = NullableY()
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>cascadeNullableGet<!>(x: NullableX?): Z? {
    contract {
        returnsNotNull() implies (x != null)
    }
    return x?.y?.z
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>cascadeNullableSmartcastGet<!>(x: NullableX?): Z? {
    contract {
        returnsNotNull() implies (x != null)
    }
    return if (x == null) null else if (x.y == null) null else x.y.z
}

<!VIPER_TEXT!>class Baz<!> {
    val x: Int = 4
}

fun <!VIPER_TEXT!>nullableReceiverNotNullSafeGet<!>() {
    val f: Baz? = Baz()
    val cond1 = f?.x != null
    verify(cond1)
}

fun <!VIPER_TEXT!>nullableReceiverNullSafeGet<!>() {
    val f: Baz? = null
    val cond1 = f?.x == null
    verify(cond1)
}

@Suppress("UNNECESSARY_SAFE_CALL")
fun <!VIPER_TEXT!>nonNullableReceiverSafeGet<!>() {
    val f: Baz = Baz()
    val cond1 = f?.x != null
    verify(cond1)
}

open <!VIPER_TEXT!>class ClassI<!>(val x: Int, val y: Int) {
    open val z: Z = Z()
}

<!VIPER_TEXT!>class ClassII<!>(final override val z: Z) : ClassI(10, 10)

fun <!VIPER_TEXT!>checkPrimary<!>(x: Int, y: Int) {
    val classI = ClassI(x, y)
    val z = Z()
    val cond1 = x != y || classI.x == classI.y
    val cond2 = ClassII(z).z == z
    verify(
        cond1,
        cond2
    )
}
