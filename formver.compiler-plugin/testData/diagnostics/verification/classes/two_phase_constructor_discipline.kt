// NEVER_VALIDATE
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!INVALID_LAW!>class EarlyPropertyRead {
    val first = 1
    val second = first
    @Law @Pure fun invariant(): Boolean = second > 0
}<!>

<!INVALID_LAW!>class EarlyThisArgument {
    val first = 1
    val second = observe(this)
    @Law @Pure fun invariant(): Boolean = second > 0
}<!>

@Pure fun <!VIPER_TEXT!>observe<!>(value: EarlyThisArgument): Int = 1

<!INVALID_LAW!>class EarlyMemberCall {
    val first = 1
    val second = value()
    @Pure fun <!VIPER_TEXT!>value<!>(): Int = 1
    @Law @Pure fun invariant(): Boolean = second > 0
}<!>

<!INVALID_LAW!>open class EarlyVirtualDispatch {
    val value = 1
    init { observe() }
    open fun <!VIPER_TEXT!>observe<!>() {}
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>

<!VERIFICATION_SKIPPED!>class TrustedEarlyRead<!> {
    val first = 1
    val second = first
}

<!INVALID_LAW!>class EarlyCapturedThis {
    val callback = { this }
    val value = 1
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>

open <!VERIFICATION_SKIPPED!>class TrustedVirtualDispatch<!> {
    val value = 1
    init { observe() }
    open fun <!VIPER_TEXT!>observe<!>() {}
}

open <!VIPER_TEXT!>class CompletedFinalCall<!> {
    val value = 1
    init { observe() }
    fun <!VIPER_TEXT!>observe<!>() {}
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!INVALID_LAW!>class ReadBeforeDelayedAssignment(flag: Boolean) {
    val first = 1
    val second: Int
    init {
        require(first > 0)
        if (flag) second = 1 else second = 2
    }
    @Law @Pure fun invariant(): Boolean = second > 0
}<!>

class AssignedThroughTry(flag: Boolean) {
    var value: Int
    init {
        try {
            if (flag) throw IllegalArgumentException()
            value = 1
        } catch (error: IllegalArgumentException) {
            value = 2
        }
        require(value > 0)
    }
}

<!VIPER_TEXT!>class CompletedAssignments<!>(flag: Boolean) {
    val value: Int
    init {
        if (flag) value = 1 else value = 2
        require(value > 0)
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}

open class DispatchBase {
    @Unique @Borrowed open fun <!VIPER_TEXT!>observe<!>() {}
}

open class DispatchFinal : DispatchBase() {
    @Unique @Borrowed final override fun <!VIPER_TEXT!>observe<!>() {}
}

open <!VIPER_TEXT!>class InheritedFinalDispatch<!> : DispatchFinal() {
    val value: Int
    init {
        value = 1
        observe()
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class FinalClassDispatch<!> : DispatchBase() {
    val value: Int
    init {
        value = 1
        observe()
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}

open class GenericFinalDispatch<T> {
    @Unique @Borrowed fun <!VIPER_TEXT!>observe<!>(argument: T) {}
}

open <!VIPER_TEXT!>class SubstitutedFinalDispatch<!> : GenericFinalDispatch<Int>() {
    val value: Int
    init {
        value = 1
        observe(value)
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}

open class GenericOpenDispatch<T> {
    @Unique @Borrowed open fun <!VIPER_TEXT!>observe<!>(argument: T) {}
}

<!INVALID_LAW!>open class SubstitutedOpenDispatch : GenericOpenDispatch<Int>() {
    val value: Int
    init {
        value = 1
        observe(value)
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>
