// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

abstract class Base {
    abstract fun inherited(x: Int): Int
}

abstract class Errors : Base() {
    <!INVALID_SPEC_OF!>@SpecOf("missing")
    fun missingSpec(x: Int) {}<!>

    <!INVALID_SPEC_OF!>@SpecOf("inherited")
    fun inheritedSpec(x: Int) {}<!>

    <!INVALID_SPEC_OF!>@SpecOf("value")
    fun wrongParametersSpec(x: Boolean) {}<!>

    <!INVALID_SPEC_OF!>@SpecOf("value")
    fun firstSpec(x: Int) {}<!>

    <!INVALID_SPEC_OF!>@SpecOf("value")
    fun secondSpec(x: Int) {}<!>

    abstract fun value(x: Int): Int

    <!INVALID_SPEC_OF!>@SpecOf("withBody")
    fun withBodySpec(x: Int) {
        preconditions { x > 0 }
    }<!>

    fun withBody(x: Int): Int {
        preconditions { x > 1 }
        return x
    }

    <!INVALID_SPEC_OF!>@SpecOf("nonUnit")
    fun nonUnitSpec(x: Int): Int = x<!>

    abstract fun nonUnit(x: Int): Int

    <!INVALID_SPEC_OF!>@SpecOf("extra")
    fun extraSpec(x: Int) {
        preconditions { x > 0 }
        val y = x
    }<!>

    abstract fun extra(x: Int): Int

    <!INVALID_SPEC_OF!>@SpecOf("result")
    fun resultSpec(x: Int) {
        postconditions<Boolean> { r -> r }
    }<!>

    abstract fun result(x: Int): Int

    <!INVALID_SPEC_OF!>@SpecOf("pure")
    fun pureSpec(x: Int) {}<!>

    @Pure
    fun pure(x: Int): Int = x

    <!INVALID_SPEC_OF!>@SpecOf("owned")
    fun ownedSpec(x: Box) {}<!>

    abstract fun owned(x: @Unique Box)

    <!INVALID_SPEC_OF!>@SpecOf("extension")
    fun Int.extensionSpec() {}<!>

    abstract fun Int.extension()

    fun callsSpec() {
        <!INVALID_SPEC_OF!>missingSpec(1)<!>
    }
}

class Box(var v: Int)
