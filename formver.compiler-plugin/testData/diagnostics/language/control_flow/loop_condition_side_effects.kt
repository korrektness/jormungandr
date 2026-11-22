import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>postIncrementCondition<!>(): Int {
    var d = 0
    while (d<!INTEGER_OVERFLOW!>++<!> < 8) { }
    return d
}
