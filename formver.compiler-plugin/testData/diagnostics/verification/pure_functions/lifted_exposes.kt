// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface ExposedCounter {
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
    @GhostObserver @Pure fun <!VIPER_TEXT!>step<!>(): Int = ghostObserver()
    @Law @Pure fun positiveStep(): Boolean = step() > 0
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke")
    fun invokeSpec() {
        postconditions<Int> { result ->
            value() == old(value()) + step()
            result == value()
            step() == old(step())
        }
    }
}

fun <!VIPER_TEXT!>createCounter<!>(): @Unique ExposedCounter {
    postconditions<ExposedCounter> { result -> result.value() == 0; result.step() == 1 }
    var n = 0
    return ExposedCounter {
        exposes(ExposedCounter::class) { value() == n; step() == 1 }
        n += 1
        n
    }
}

fun <!VIPER_TEXT!>useCreatedCounter<!>() {
    val counter: @Unique ExposedCounter = createCounter()
    verify(counter.value() == 0)
    val result = counter.invoke()
    verify(result == 1, counter.value() == 1)
    refute(false)
}

fun interface ConstantObservation {
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
    @Law @Pure fun seven(): Boolean = value() == 7
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke") fun invokeSpec() {
        postconditions<Int> { result -> result == value() }
    }
}

fun interface ConstantClosure : ConstantObservation

fun <!VIPER_TEXT!>constantBinding<!>(): @Unique ConstantClosure {
    postconditions<ConstantClosure> { result -> result.value() == 7 }
    return ConstantClosure {
        exposes(ConstantClosure::class) { value() == 7 }
        7
    }
}

fun <!VIPER_TEXT!>breaksBoundContract<!>(): @Unique ConstantClosure = ConstantClosure <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    exposes(ConstantClosure::class) { value() == 7 }
    8
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>breaksInheritedLaw<!>(): @Unique ConstantClosure = ConstantClosure {
    exposes(ConstantClosure::class) { value() == 8 }
    8
}<!>

fun <!VIPER_TEXT!>multipleLawObligations<!>() {
    val first: @Unique ConstantClosure = ConstantClosure {
        exposes(ConstantClosure::class) { value() == 7 }
        7
    }
    val second: @Unique ConstantClosure = ConstantClosure {
        exposes(ConstantClosure::class) { value() == 7 }
        7
    }
    val a = first.invoke()
    val b = second.invoke()
    verify(a == b)
    refute(false)
}
