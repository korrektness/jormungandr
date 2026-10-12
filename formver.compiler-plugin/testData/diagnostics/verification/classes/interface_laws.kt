// RENDER_PREDICATES
// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

interface SizedLaw {
    @Pure @Unique
    fun <!VIPER_TEXT!>size<!>(): Int

    @Law @Pure @Unique
    fun nonnegative(): Boolean = size() >= 0
}

interface SizedLawLeft : SizedLaw
interface SizedLawRight : SizedLaw
interface SizedLawDiamond : SizedLawLeft, SizedLawRight {
    @Law @Pure @Unique
    fun bounded(): Boolean {
        val count = size()
        return count <= 10
    }
}

open <!VIPER_TEXT!>class GoodSizedLaw<!> : SizedLawDiamond {
    @Pure @Unique
    override fun <!VIPER_TEXT!>size<!>(): Int = 3
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class BrokenSizedLaw<!> : SizedLawDiamond {
    @Pure @Unique
    override fun <!VIPER_TEXT!>size<!>(): Int = -1
}<!>

<!VIPER_TEXT!>class InheritedSizedLaw<!> : GoodSizedLaw()

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class FieldSizedLaw<!>(var count: Int) : SizedLaw {
    @Pure @Unique
    override fun <!VIPER_TEXT!>size<!>(): Int = count
}<!>

@AlwaysVerify
fun <!VIPER_TEXT!>borrowedLawTransfer<!>() {
    val value: @Unique InheritedSizedLaw = InheritedSizedLaw()
    lawClient(value)
    verify(value.size() == 3)
    val view: @Unique SizedLawDiamond = value
    inheritedLawClient(view)
    verify(view.size() >= 0)
    refute(false)
}

@AlwaysVerify
fun <!VIPER_TEXT!>lawPostcondition<!>(view: @Unique @Borrowed SizedLawDiamond): Int {
    postconditions<Int> { result -> result >= 0 && result <= 10 }
    return view.size()
}

@AlwaysVerify
fun <!VIPER_TEXT!>lawClient<!>(view: @Unique @Borrowed SizedLaw) {
    verify(view.size() >= 0)
    refute(false)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inheritedLawClient<!>(view: @Unique @Borrowed SizedLawDiamond) {
    verify(view.size() >= 0)
    verify(view.size() <= 10)
    refute(false)
}
