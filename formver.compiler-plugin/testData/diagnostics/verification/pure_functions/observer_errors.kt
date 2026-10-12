// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

interface Measure {
    @Pure
    fun scaled(factor: Int): Int

    @Pure
    val size: Int

    fun plain(x: Int): Int

    <!INVALID_OBSERVER!>@Pure
    var count: Int<!>
}

interface Refined : Measure {
    @Pure
    override fun scaled(factor: Int): Int

    <!INVALID_OBSERVER!>override val size: Int<!>
}

class Accepted : Measure {
    @Pure
    override fun scaled(factor: Int): Int = factor * 2

    @Pure
    override val size: Int
        get() = 3

    override fun plain(x: Int): Int = x

    override var count: Int = 0
}

class NotPure : Measure {
    <!INVALID_OBSERVER!>override fun scaled(factor: Int): Int = factor<!>

    <!INVALID_OBSERVER!>override val size: Int = 1<!>

    <!INVALID_OBSERVER!>@Pure
    override fun plain(x: Int): Int = x<!>

    override var count: Int = 0
}

abstract class Bodiless : Measure {
    <!INVALID_OBSERVER!>@Pure
    abstract override fun scaled(factor: Int): Int<!>

    <!INVALID_OBSERVER!>@Pure
    abstract override val size: Int<!>
}

interface Sized {
    @Pure
    val size: Int

    fun grow(): Int
}

<!INVALID_OBSERVER!>open class Delegating(inner: Sized) : Sized by inner<!>

class DelegatingChild(inner: Sized) : Delegating(inner)

open class Base {
    open fun scaled(factor: Int): Int = factor
}

<!INVALID_OBSERVER!>open class Inheriting : Base(), Measure {
    <!INVALID_OBSERVER!>@Pure
    override val size: Int = 2<!>

    override fun plain(x: Int): Int = x

    override var count: Int = 0
}<!>

class InheritingChild : Inheriting()

class Holder {
    @Pure
    val value: Int = 0
}
