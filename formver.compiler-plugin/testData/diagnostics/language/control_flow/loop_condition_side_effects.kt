import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>postIncrementCondition<!>(): Int {
    var d = 0
    while (d<!INTEGER_OVERFLOW!>++<!> < 8) { }
    return d
}

fun <!VIPER_TEXT!>postIncrementConditionWithInvariant<!>(): Int {
    var d = 0
    while (d++ < 8) {
        loopInvariants { d <= 9 }
    }
    return d
}

fun <!VIPER_TEXT!>breakExitChecksInvariant<!>(): Int {
    var d = 0
    while (d++ < 8) {
        loopInvariants { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>d <= 9<!> }
        if (d == 5) {
            d = 100
            break
        }
    }
    return d
}
