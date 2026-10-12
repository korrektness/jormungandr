// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Initialized<!>(start: Int) {
    var value = start
    init { if (value < 0) value = 0 }
    @Law @Pure fun invariant(): Boolean = value >= 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class BrokenInitialization<!> {
    var value = 1
    init { value = -1 }
    @Law @Pure fun invariant(): Boolean = value >= 0
}<!>

<!VIPER_TEXT!>class OrderedInitialization<!> {
    var value = 1
    init { value += 2 }
    val snapshot = value
    init { value += snapshot }
    @Law @Pure fun invariant(): Boolean = value == 6 && snapshot == 3
}

<!VIPER_TEXT!>class InitializedLater<!>(start: Int) {
    var value: Int
    init { value = if (start > 0) start else 1 }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class CheckedInitialization<!>(val value: Int) {
    init { require(value >= 0) }
    @Law @Pure fun invariant(): Boolean = value >= 0
}

fun <!VIPER_TEXT!>positiveInitializer<!>(value: Int): Int {
    postconditions<Int> { result -> result > 0 }
    return if (value > 0) value else 1
}

<!VIPER_TEXT!>class EffectfulInitialization<!>(start: Int) {
    val value = positiveInitializer(start)
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class BrokenInitializer<!>(start: Int) {
    val value = start - 1
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>

@Pure fun <!VIPER_TEXT!>initializedHead<!>(data: @Unique @Borrowed IntArray): Int = if (data.size > 0) data[0] else 0

<!VIPER_TEXT!>class OwnedInitialization<!>(val data: @Unique IntArray) {
    init {
        require(data.size > 0)
        data[0] = 1
    }
    @Law @Pure @Unique fun invariant(): Boolean = initializedHead(data) > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class BrokenOwnedInitialization<!>(var data: @Unique IntArray) {
    init {
        require(data.size > 0)
        data[0] = -1
    }
    @Law @Pure @Unique fun invariant(): Boolean = initializedHead(data) > 0
}<!>

<!VIPER_TEXT!>class AssignedImmutable<!>(start: Int) {
    val value: Int
    init { value = if (start > 0) start else 1 }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class LoopInitialization<!> {
    var value = 0
    init {
        var index = 0
        while (index < 3) {
            loopInvariants { index >= 0 && index <= 3 && value == index }
            value++
            index++
        }
    }
    @Law @Pure fun invariant(): Boolean = value == 3
}

<!VIPER_TEXT!>class LocalOwnedInitialization<!> {
    var data: @Unique IntArray = IntArray(1)
    init {
        val replacement: @Unique IntArray = IntArray(1)
        replacement[0] = 1
        data = replacement
    }
    @Law @Pure @Unique fun invariant(): Boolean = initializedHead(data) > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class BrokenLocalOwnedInitialization<!> {
    var data: @Unique IntArray = IntArray(1)
    init {
        val replacement: @Unique IntArray = IntArray(1)
        replacement[0] = -1
        data = replacement
    }
    @Law @Pure @Unique fun invariant(): Boolean = initializedHead(data) > 0
}<!>

<!VIPER_TEXT!>class ObservedBeforeInitialization<!>(val data: @Unique IntArray) {
    val snapshot = initializedHead(data)
    init { require(data.size > 0); data[0] = 1 }
    @Law @Pure @Unique fun invariant(): Boolean = initializedHead(data) > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class SharedBorrowedInitialization<!> {
    var value = 1
    init { clear() }
    @Borrowed fun <!VIPER_TEXT!>clear<!>() { value = -1 }
    @Law @Pure fun invariant(): Boolean = value >= 0
}<!>

<!VIPER_TEXT!>class SharedBorrowedCheckedInitialization<!> {
    var value = 1
    init { preserve(); require(value >= 0) }
    @Borrowed fun <!VIPER_TEXT!>preserve<!>() { value = 2 }
    @Law @Pure fun invariant(): Boolean = value >= 0
}

<!VIPER_TEXT!>class SharedBorrowedPreservedInitialization<!> {
    val value = 1
    var scratch = 0
    init { preserve() }
    @Borrowed fun <!VIPER_TEXT!>preserve<!>() { scratch = 2 }
    @Law @Pure fun invariant(): Boolean = value >= 0
}

<!VIPER_TEXT!>class LawfulConstructionChild<!>(var value: Int) {
    init { require(value >= 0) }
    @Law @Pure fun invariant(): Boolean = value >= 0
}
@Pure fun <!VIPER_TEXT!>borrowedChildValue<!>(child: @Unique @Borrowed LawfulConstructionChild): Int {
    postconditions<Int> { it >= 0 }
    return child.value
}

<!UNSUPPORTED_FEATURE!>class BrokenNestedBorrowedInitialization(val child: @Unique LawfulConstructionChild) {
    init { corrupt() }
    @Borrowed fun <!VIPER_TEXT!>corrupt<!>() { child.value = -1 }
    @Law @Pure @Unique fun invariant(): Boolean = borrowedChildValue(child) >= 0
}<!>

<!UNSUPPORTED_FEATURE!>class ForwardBorrowedImmutableInitialization {
    var before = 0
    init { before = readAge() }
    val age = 1
    @Borrowed fun <!VIPER_TEXT!>readAge<!>(): Int {
        postconditions<Int> { it == age }
        return age
    }
    @Law @Pure fun invariant(): Boolean = before >= 1
}<!>

fun <!VIPER_TEXT!>initializedClient<!>(start: Int) {
    val instance: @Unique Initialized = Initialized(start)
    verify(instance.value >= 0)
    val ordered: @Unique OrderedInitialization = OrderedInitialization()
    verify(ordered.value == 6 && ordered.snapshot == 3)
    val later: @Unique InitializedLater = InitializedLater(start)
    verify(later.value > 0)
    val checked: @Unique CheckedInitialization = CheckedInitialization(start)
    verify(checked.value >= 0)
    val effectful: @Unique EffectfulInitialization = EffectfulInitialization(start)
    verify(effectful.value > 0)
    val assigned: @Unique AssignedImmutable = AssignedImmutable(start)
    verify(assigned.value > 0)
    val loop: @Unique LoopInitialization = LoopInitialization()
    verify(loop.value == 3)
}
