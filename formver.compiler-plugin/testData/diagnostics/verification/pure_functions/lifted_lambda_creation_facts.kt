// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface GrowingCounter {
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke")
    fun invokeSpec() {
        postconditions<Int> { result -> result > old(state()) && result == state() }
    }
    @GhostObserver @Pure fun <!VIPER_TEXT!>state<!>(): Int = ghostObserver()
}

fun <!VIPER_TEXT!>elseBranchCounter<!>(step: Int): @Unique GrowingCounter {
    var n = 0
    return if (step <= 0) GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += 1
        n
    } else GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>preconditionCounter<!>(step: Int): @Unique GrowingCounter {
    preconditions { step > 0 }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>relationalCounter<!>(low: Int, high: Int): @Unique GrowingCounter {
    preconditions { high > low }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += high - low
        n
    }
}

fun <!VIPER_TEXT!>guardCounter<!>(step: Int): @Unique GrowingCounter {
    var n = 0
    if (step <= 0) return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += 1
        n
    }
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>whenSubjectCounter<!>(step: Int): @Unique GrowingCounter {
    preconditions { step >= 0 }
    var n = 0
    return when (step) {
        0 -> GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += 1
            n
        }
        else -> GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += step
            n
        }
    }
}

fun <!VIPER_TEXT!>nestedCounter<!>(step: Int): @Unique GrowingCounter {
    preconditions { step > 0 }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        var m = 0
        val inner: @Unique GrowingCounter = GrowingCounter {
            exposes(GrowingCounter::class) { state() == m }
            m += step
            m
        }
        n += inner.invoke()
        n
    }
}

@NeverConvert
fun consumeCounter(counter: @Unique GrowingCounter): Boolean = true

fun <!VIPER_TEXT!>shortCircuitCounter<!>(step: Int) {
    var n = 0
    step > 0 && consumeCounter(GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    })
}

fun <!VIPER_TEXT!>loopCounter<!>(step: Int): @Unique GrowingCounter {
    var n = 0
    while (step > 0) {
        return GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += step
            n
        }
    }
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += 1
        n
    }
}

fun <!VIPER_TEXT!>disjunctiveGuardCounter<!>(step: Int, enabled: Boolean): @Unique GrowingCounter {
    var n = 0
    return if (step <= 0 || enabled) GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += 1
        n
    } else GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>nullableCounter<!>(step: Int?): @Unique GrowingCounter {
    preconditions { step != null && step > 0 }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        if (step != null) n += step
        n
    }
}

@Pure
fun <!VIPER_TEXT!>positiveStep<!>(step: Int): Boolean = step > 0

fun <!VIPER_TEXT!>pureFactCounter<!>(step: Int): @Unique GrowingCounter {
    preconditions { positiveStep(step) }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

fun <!VIPER_TEXT!>quantifiedCounter<!>(step: Int): @Unique GrowingCounter {
    preconditions { forAll<Int> { (it == it) implies (step > 0) } }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}

inline fun <!VIPER_TEXT!>conditionalContexts<!>(step: Int, make: () -> Unit) {
    if (step > 0) make()
    make()
}

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>multipleCreationContexts<!>(step: Int) {
    conditionalContexts(step) {
        var n = 0
        consumeCounter(GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += step
            n
        })
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>breakContinuationCounter<!>(step: Int): @Unique GrowingCounter {
    var n = 0
    if (step <= 0) {
        while (step <= 0) { break }
    }
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>conditionalReturnCounter<!>(step: Int, stop: Boolean): @Unique GrowingCounter {
    var n = 0
    if (step <= 0) {
        if (stop) return GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += 1
            n
        }
    }
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>nestedGuardDoesNotLeak<!>(step: Int, a: Boolean): @Unique GrowingCounter {
    var n = 0
    if (a) {
        if (step <= 0) return GrowingCounter {
            exposes(GrowingCounter::class) { state() == n }
            n += 1
            n
        }
    }
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}<!>

class ImmutableStep(val amount: Int)
class MutableStep(var amount: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>positive<!>(): Boolean = amount > 0
}

fun <!VIPER_TEXT!>immutableHeapCounter<!>(box: @Unique ImmutableStep): @Unique GrowingCounter {
    preconditions { box.amount > 0 }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += box.amount
        n
    }
}

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>mutableHeapCounter<!>(box: @Unique MutableStep): @Unique GrowingCounter {
    preconditions { box.amount > 0 }
    var n = 0
    box.amount = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += box.amount
        n
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>mutableHelperCounter<!>(box: @Unique MutableStep): @Unique GrowingCounter {
    preconditions { box.positive() }
    var n = 0
    box.amount = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += box.amount
        n
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>uncapturedStateCounter<!>(step: Int, other: Int): @Unique GrowingCounter {
    preconditions { step > other && other >= 0 }
    var n = 0
    return GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        n
    }
}<!>

<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>mutableCaptureCounter<!>(initial: Int): @Unique GrowingCounter {
    preconditions { initial > 0 }
    var step = initial
    var n = 0
    return if (step > 0) GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += step
        step = 0
        n
    } else GrowingCounter {
        exposes(GrowingCounter::class) { state() == n }
        n += 1
        n
    }
}<!>
