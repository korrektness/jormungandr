// FULL_JDK


import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>anyIntegerSquaredAtLeastZero<!>(): Int {
    postconditions<Int> { res ->
        forAll<Int> {
            (-46340 <= it && it <= 46340) implies (it * it >= 0)
            (-46340 <= it && it <= 46340) implies (it * it >= res)
        }
    }
    return 0
}

fun <!VIPER_TEXT!>anyIntegerSquaredIsAtLeastOneExceptZero<!>(): Int {
    postconditions<Int> { res ->
        forAll<Int> {
            (-46340 <= it && it <= 46340 && it != 0) implies (it * it >= res)
        }
    }
    return 1
}

fun <!VIPER_TEXT!>anyIntegerSquaredIsAtLeastZeroStringVersion<!>(str: String): Int {
    var res = 0
    var i = 10
    while (i > 0) {
        loopInvariants {
            forAll<Int> {
                (0 <= it && it < str.length) implies ((str[it] - 'a').toLong() * (str[it] - 'a').toLong() >= res)
            }
        }
        i--
    }
    return res
}
