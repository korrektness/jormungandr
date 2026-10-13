// FULL_JDK
// REPLACE_STDLIB_EXTENSIONS

import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.preconditions
import org.jetbrains.kotlin.formver.plugin.loopInvariants
import org.jetbrains.kotlin.formver.plugin.verify

// Incrementing i violates the stated invariant.
fun <!VIPER_TEXT!>violatedInvariant<!>() {
    var i = 0
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>while (i < 2) {
        loopInvariants { i < 1 }
        i = i + 1
    }<!>
}

fun <!VIPER_TEXT!>test<!>(n: Int) {
    var it = 0
    var holds = true
    while (it < 10) {
        loopInvariants {
            it <= 10
            holds
        }
        it = it + 1
    }
    verify(it == 10)

    if (it <= n) {
        while (it < n) {
            loopInvariants {
                it <= n
                holds
            }
            it = it + 1
        }
        verify(it == n)
    }
}

fun <!VIPER_TEXT!>loopInsideLoop<!>() {
    var i = 0
    while (i < 10) {
        loopInvariants {
            i <= 10
        }
        var j = i + 1
        while (j < 10) {
            loopInvariants {
                i < j
                j <= 10
            }
            j = j + 1
        }
        i = i + 1
    }
}

fun <!VIPER_TEXT!>withBreak<!>() {
    var i = 0
    while (true) {
        loopInvariants {
            i <= 10
        }
        if (i >= 10) break
        i++
    }
    verify(i == 10)
}

<!VIPER_TEXT!>class WithVar<!>(var e: Int) {
    @NeverConvert
    fun doSomething() = e > 10
}

fun <!VIPER_TEXT!>test_boolean_postcondition<!>() {
    val withVar = WithVar(42)
    var boolean = true
    while (boolean) {
        boolean = withVar.doSomething()
    }
    verify(!boolean)
}

<!VIPER_TEXT!>class ClassWithField<!>(val field: Int)

fun <!VIPER_TEXT!>test_while<!>(param: ClassWithField) {
    preconditions { param.field >= -46339 }
    val c = ClassWithField(13)
    val initParamField = param.field

    // Inserting an additional scope here to check that
    // while invariants don't capture variables from there.
    var iteration =
        if (initParamField > 0) 0
        else {
            val intermediate = -initParamField + 1
            verify(1 <= intermediate && intermediate <= 46340)
            intermediate * intermediate
        }
    while (iteration < 10) {
        val field = c.field
        val paramField = param.field
        iteration = iteration + 1
    }
    val cond1 = c.field == 13
    val cond2 = initParamField == param.field
    verify(cond1)
    verify(cond2)
}

fun <!VIPER_TEXT!>test_while_with_inlining<!>(param: ClassWithField) {
    val local = ClassWithField(13)
    ClassWithField(42).run {
        var iteration = 0
        while (iteration < 10) {
            val paramField = param.field
            val localField = local.field
            val thisField = field
            iteration = iteration + 1
        }
        verify(field == 42)
        verify(local.field == 13)
    }
}

fun <!VIPER_TEXT!>test_while_with_smartcast<!>(param: Any, innerParam: Any) {
    if (param is ClassWithField) {
        var iteration = 0
        while (iteration < 10) {
            val paramField = param.field
            if (innerParam is ClassWithField) {
                val innerParamField = innerParam.field
            }
            iteration = iteration + 1
        }
    }
}
