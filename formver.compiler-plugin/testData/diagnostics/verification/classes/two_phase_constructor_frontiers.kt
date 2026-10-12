// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class BranchFrontier<!>(flag: Boolean) {
    val value: Int
    init {
        if (flag) value = 1 else value = 2
        touch()
        require(value > 0)
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>touch<!>() {}
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class LocalRepair<!> {
    var value = -1
    init { value = 1 }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class PositiveParameter<!>(val value: Int) {
    init { require(value > 0) }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class LeakBeforeRequire<!>(val value: Int) {
    init {
        observeConstruction(this)
        require(value > 0)
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>

fun <!VIPER_TEXT!>observeConstruction<!>(value: @Borrowed LeakBeforeRequire) {}

<!VIPER_TEXT!>class AbnormalExit<!>(val value: Int) {
    init { if (value <= 0) error("invalid") }
    @Law @Pure fun invariant(): Boolean = value > 0
}

fun <!VIPER_TEXT!>positiveParameterClient<!>() {
    val positive = PositiveParameter(3)
    verify(positive.value > 0)
    val repaired: @Unique LocalRepair = LocalRepair()
    verify(repaired.value > 0)
}

<!VIPER_TEXT!>class PhaseTwoCall<!> {
    var value = 1
    init { increase() }
    @Unique @Borrowed fun <!VIPER_TEXT!>increase<!>() { value++ }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class SecondaryPhaseTwo<!> {
    var value = 1
    constructor()
    constructor(start: Int): this() {
        require(start > 0)
        value = start
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class SecondaryBroken<!> {
    var value = 1
    constructor()
    constructor(broken: Boolean): this() {
        if (broken) value = -1
    }
    @Law @Pure fun invariant(): Boolean = value > 0
}<!>

open <!VIPER_TEXT!>class EstablishedParent<!> {
    var value = 1
    init { increase() }
    @Unique @Borrowed fun <!VIPER_TEXT!>increase<!>() { value++ }
    @Law @Pure fun invariant(): Boolean = value > 0
}

<!VIPER_TEXT!>class EstablishedChild<!>: EstablishedParent() {
    init { value++ }
}
