// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

fun interface ParameterSpec {
    fun invoke(x: Int): Int
    @GhostObserver @Pure fun state(): Int = ghostObserver()
}

fun bindsParameter(): ParameterSpec = ParameterSpec { x ->
    <!INVALID_EXPOSES!>exposes(ParameterSpec::class) { state() == x }<!>
    x
}

fun bindsNestedParameter(): ParameterSpec = ParameterSpec { x ->
    <!INVALID_EXPOSES!>exposes(ParameterSpec::class) { state() == if (x > 0) 1 else 0 }<!>
    x
}

fun bindsCapture(start: Int): ParameterSpec = ParameterSpec { x ->
    exposes(ParameterSpec::class) { state() == start }
    x
}
