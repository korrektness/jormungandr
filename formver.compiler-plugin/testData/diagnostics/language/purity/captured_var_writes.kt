// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>localFunction<!>() {
    var c = 0
    fun <!VERIFICATION_SKIPPED!>increment<!>() { verify(<!PURITY_VIOLATION!>c++ == 0<!>) }
    fun <!VERIFICATION_SKIPPED!>decrement<!>() { verify(<!PURITY_VIOLATION!>--c == 0<!>) }
    fun <!VERIFICATION_SKIPPED!>assign<!>() { verify(<!PURITY_VIOLATION!>run { c = 1; true }<!>) }
    fun <!VERIFICATION_SKIPPED!>plusAssign<!>() { verify(<!PURITY_VIOLATION!>run { c += 1; true }<!>) }
    fun <!VIPER_TEXT!>writeOutsideCondition<!>() {
        c = 3
    }
    increment()
    decrement()
    assign()
    plusAssign()
    writeOutsideCondition()
}

fun <!VIPER_TEXT!>localClass<!>() {
    var c = 0
    <!VIPER_TEXT!>class Counter<!> {
        fun <!VERIFICATION_SKIPPED!>increment<!>() { verify(<!PURITY_VIOLATION!>c++ == 0<!>) }
        fun <!VERIFICATION_SKIPPED!>plusAssign<!>() { verify(<!PURITY_VIOLATION!>run { c += 1; true }<!>) }
    }
    Counter().increment()
}

fun <!VIPER_TEXT!>lambdas<!>() {
    var c = 0
    val increment = <!VERIFICATION_SKIPPED!>{ verify(<!PURITY_VIOLATION!>c++ == 0<!>) }<!>
    val assign = <!VERIFICATION_SKIPPED!>{ verify(<!PURITY_VIOLATION!>run { c = 1; true }<!>) }<!>
    val writeOutsideCondition = { c = 2 }
    increment()
    assign()
    writeOutsideCondition()
}
