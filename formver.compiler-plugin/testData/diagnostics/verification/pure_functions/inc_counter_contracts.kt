// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface ArgumentIncSpec {
    @GhostObserver @Pure fun <!VIPER_TEXT!>inc<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(x: Int): Int
    @SpecOf("invoke")
    fun invokeSpec(x: Int) {
        preconditions { x >= 0 && x <= 2147483645 }
        postconditions<Int> { result -> result >= x }
    }
}

fun <!VIPER_TEXT!>weakerPrecondition<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 && x <= 2147483645 }
    postconditions<Int> { result -> result == x + 1 }
    x + 1
}

fun <!VIPER_TEXT!>postconditionWithBound<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x < 2147483647 }
    postconditions<Int> { result -> result == x + 1 }
    x + 1
}

fun <!VIPER_TEXT!>preconditionOnly<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec <!OVERRIDE_NOT_REFINING!>{ x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 && x <= 2147483645 }
    x + 1
}<!>

fun <!VIPER_TEXT!>incorrectOwnBody<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 && x <= 2147483645 }
    postconditions<Int> { result -> result == x + 1 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x + 2<!>
}

fun <!VIPER_TEXT!>interfaceClient<!>() {
    val f: @Unique ArgumentIncSpec = weakerPrecondition()
    val result = f(0)
    verify(result >= 0)
}

fun <!VIPER_TEXT!>clientCannotUseOwnPrecondition<!>() {
    val f: @Unique ArgumentIncSpec = weakerPrecondition()
    <!VIPER_VERIFICATION_ERROR!>f(-1)<!>
}

fun <!VIPER_TEXT!>clientCannotUseOwnPostcondition<!>() {
    val f: @Unique ArgumentIncSpec = weakerPrecondition()
    val result = f(0)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result == 1<!>)
}
