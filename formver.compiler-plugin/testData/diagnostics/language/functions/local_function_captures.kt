// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>capturedParameter<!>(n: Int): Int {
    preconditions { n > 0 }
    fun <!VIPER_TEXT!>positive<!>(): Boolean {
        verify(n > 0)
        return true
    }
    return if (positive()) n else 0
}

fun <!VIPER_TEXT!>pathFact<!>(n: Int, m: Int): Int {
    if (n < 10) {
        val k = m
        fun <!VIPER_TEXT!>bounded<!>(): Int {
            verify(n < 10)
            return n + 1
        }
        return bounded() + 0 * k
    }
    return 0
}

fun <!VIPER_TEXT!>valueIsNotAFact<!>(n: Int): Int {
    val m = n
    fun <!VIPER_TEXT!>same<!>(): Boolean {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>m == n<!>)
        return true
    }
    return if (same()) 1 else 0
}

fun <!VIPER_TEXT!>capturedVarReadInside<!>(): Int {
    var count = 0
    fun <!VIPER_TEXT!>read<!>(): Int {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>count == 0<!>)
        return count
    }
    return read()
}

fun <!VIPER_TEXT!>capturedVarSharedAfterDeclaration<!>(): Int {
    var count = 0
    verify(count == 0)
    fun <!VIPER_TEXT!>reset<!>() { count = 5 }
    reset()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>count == 5<!>)
    return count
}

fun <!VIPER_TEXT!>capturedVarSharedInLoop<!>(n: Int) {
    var seen = 0
    var i = 0
    while (i < n) {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>seen == 0<!>)
        fun <!VIPER_TEXT!>mark<!>() { seen = 1 }
        mark()
        i = i + 1
    }
}

fun <!VIPER_TEXT!>capturedVarSharedInForBody<!>(n: Int) {
    for (i in 0 until n) {
        var seen = 0
        var j = 0
        while (j < 2) {
            verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>seen == 0<!>)
            fun <!VIPER_TEXT!>mark<!>() { seen = 1 }
            mark()
            j = j + 1
        }
    }
}

fun <!VIPER_TEXT!>shadowedCapture<!>(x: Int) {
    preconditions { x > 0 }
    fun <!VIPER_TEXT!>outer<!>() {
        val y = x
        if (y > 0) {
            val x = -1
            fun <!VIPER_TEXT!>inner<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x > 0<!>) }
            inner()
        }
    }
    outer()
}

fun <!VIPER_TEXT!>nestedCapture<!>(n: Int): Int {
    preconditions { n > 0 }
    fun <!VIPER_TEXT!>outer<!>(): Int {
        fun <!VIPER_TEXT!>inner<!>(): Int {
            verify(n > 0)
            return n
        }
        return inner()
    }
    return outer()
}

fun <!VERIFICATION_SKIPPED!>contractReadsCapture<!>(n: Int): Int {
    <!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>get<!>(): Int {
        postconditions<Int> { it == n }
        return n
    }<!>
    <!UNSUPPORTED_FEATURE!>return get()<!>
}

fun <!VERIFICATION_SKIPPED!>twoLocalFunctionsWithOneName<!>(b: Boolean): Int {
    if (b) {
        fun <!VIPER_TEXT!>pick<!>(): Int = 1
        return pick()
    } else {
        <!UNSUPPORTED_FEATURE!>fun <!VIPER_TEXT!>pick<!>(): Int = 2<!>
        return pick()
    }
}

<!VIPER_TEXT!>class Holder<!>(val v: Int) {
    fun <!VIPER_TEXT!>capturedReceiver<!>(): Int {
        preconditions { v > 0 }
        fun <!VIPER_TEXT!>get<!>(): Int {
            verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v > 0<!>)
            return v
        }
        return get()
    }

    fun <!VIPER_TEXT!>receiverInLocalClass<!>(): Int {
        <!VIPER_TEXT!>class Reader<!> {
            fun <!VIPER_TEXT!>read<!>(): Int = v
        }
        return Reader().read()
    }

    fun <!VERIFICATION_SKIPPED!>contractReadsReceiver<!>(): Int {
        <!UNSUPPORTED_FEATURE!>fun <!VERIFICATION_SKIPPED!>get<!>(): Int {
            postconditions<Int> { it == v }
            return v
        }<!>
        <!UNSUPPORTED_FEATURE!>return get()<!>
    }
}

fun Holder.<!VIPER_TEXT!>capturedExtensionReceiver<!>(): Int {
    fun Int.<!VIPER_TEXT!>isV<!>(): Boolean = this == v
    return if (1.isV()) 1 else 0
}

fun <!VIPER_TEXT!>localFunctionInLambda<!>(n: Int): Int {
    preconditions { n > 0 }
    val f = { fun <!VIPER_TEXT!>inner<!>(): Int { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n > 0<!>); return n }; inner() }
    return f()
}
