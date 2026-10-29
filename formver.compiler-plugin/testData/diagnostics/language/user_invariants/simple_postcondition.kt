// FULL_JDK


import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>testGreater<!>(init: Int) : Int {
    preconditions { init <= 2147483642 }
    postconditions<Int> { result ->
        result > init
    }

    return init + 5
}

fun <!VIPER_TEXT!>testString<!>() : Char {
    postconditions<Char> { res ->
        res == 'a'
    }
    return "bab"[1]
}

fun <!VIPER_TEXT!>testWithPrecondition<!>(int: Int): Int {
    preconditions {
        int > 10
    }
    postconditions<Int> {
        it > 0
    }
    return int - 10
}

fun <!VIPER_TEXT!>returnGreater13<!>(): Int {
    postconditions<Int> {
        it > 13
    }
    return 16
}

fun <!VIPER_TEXT!>testPostconditionIsUsedByPrecondition<!>() = testWithPrecondition(returnGreater13())
