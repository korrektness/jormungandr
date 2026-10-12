// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface ArgumentIncSpec {
    @GhostObserver @Pure fun <!VIPER_TEXT!>inc<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(x: Int): Int
    @SpecOf("invoke")
    fun invokeSpec(x: Int) {
        preconditions { x >= 0 }
        postconditions<Int> { result -> result >= x }
    }
}

fun <!VIPER_TEXT!>weakerPrecondition<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 }
    postconditions<Int> { result -> result == x + 1 }
    x + 1
}

fun <!VIPER_TEXT!>postconditionOnly<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    postconditions<Int> { result -> result == x + 1 }
    x + 1
}

fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>preconditionOnly<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 }
    x + 1
}

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>incorrectOwnBody<!>(): @Unique ArgumentIncSpec = ArgumentIncSpec { x ->
    exposes(ArgumentIncSpec::class) { inc() == 1 }
    preconditions { x >= -1 }
    postconditions<Int> { result -> result == x + 1 }
    x + 2
}<!>

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
    verify(<!VIPER_VERIFICATION_ERROR!>result == 1<!>)
}
