// FULL_JDK
// LANGUAGE: +ContextParameters
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

<!INVALID_LAW!>@Law @Pure fun topLevel(): Boolean = true<!>

class Concrete {
    @Law @Pure fun law(): Boolean = true
}

const val limit = 0
var global = 0
@Pure fun helper(): Boolean = true
@Pure fun isSorted(first: Int, last: Int): Boolean = first <= last

class Helpers {
    companion object {
        @Pure fun check(value: Int): Boolean = value >= 0
    }
}

fun companionProvider(): Helpers.Companion = Helpers

interface Laws {
    <!INVALID_OBSERVER!>@Pure val size: Int<!>
    @Pure fun measure(factor: Int): Int
    val ordinary: Int
    <!INVALID_OBSERVER!>@Pure val other: Laws<!>
    fun impure(): Boolean
    @Pure fun memberHelper(): Boolean = true

    @Law @Pure fun accepted(): Boolean = size >= 0 && measure(2) >= size
    @Law @Pure fun values(): Boolean {
        val n = size + 1
        return n > size
    }
    @Law @Pure fun literal(): Boolean = true
    @Law @Pure fun constant(): Boolean = size >= limit
    @Law @Pure fun explicitThis(): Boolean = this.size >= this.measure(0)
    @Law @Pure fun stringValue(): Boolean = "abc".length == 3 && "abc"[0] == 'a'

    <!INVALID_LAW!>@Law fun notPure(): Boolean = true<!>
    <!INVALID_LAW!>@Law @Pure fun wrongType(): Int = 0<!>
    <!INVALID_LAW!>@Law @Pure fun nullable(): Boolean? = true<!>
    <!INVALID_LAW!>@Law @Pure fun missing(): Boolean<!>
    <!INVALID_LAW!>@Law @Pure fun parameter(n: Int): Boolean = true<!>
    <!INVALID_LAW!>@Law @Pure fun String.extension(): Boolean = true<!>
    <!INVALID_LAW!>@Law @Pure fun <T> generic(): Boolean = true<!>
    <!INVALID_LAW!>context(n: Int)
    @Law @Pure fun contextual(): Boolean = true<!>
    <!INVALID_LAW!>@Law fun several(n: Int): Int<!>

    @Law @Pure fun ordinaryRead(): Boolean = <!INVALID_LAW!>ordinary<!> > 0
    @Law @Pure fun globalRead(): Boolean = <!INVALID_LAW!>global<!> > 0
    @Law @Pure fun otherObserver(): Boolean = <!INVALID_LAW!>other.size<!> > 0
    @Law @Pure fun badCall(): Boolean = <!INVALID_LAW!>impure()<!>
    @Law @Pure fun pureHelper(): Boolean = helper()
    @Law @Pure fun pureHelperArguments(): Boolean = isSorted(size, measure(1))
    @Law @Pure fun companionHelper(): Boolean = Helpers.check(size)
    @Law @Pure fun explicitCompanionHelper(): Boolean = Helpers.Companion.check(size)
    @Law @Pure fun invalidCompanionDispatch(): Boolean = <!INVALID_LAW!>companionProvider()<!>.check(size)
    @Law @Pure fun invalidHelperArgument(): Boolean = isSorted(<!INVALID_LAW!>global<!>, size)
    @Law @Pure fun invalidHelperReceiver(): Boolean = Helpers.check(<!INVALID_LAW!>other.size<!>)
    @Law @Pure fun invalidPureMember(): Boolean = <!INVALID_LAW!>memberHelper()<!>
    @Law @Pure fun alias(): Boolean {
        val alias = <!INVALID_LAW!>this<!>
        return <!INVALID_LAW!>alias.size<!> > 0
    }
    @Law @Pure fun mutation(): Boolean {
        <!INVALID_LAW!>global = 1<!>
        return true
    }
    @Law @Pure fun mutableLocal(): Boolean {
        <!INVALID_LAW!>var n = 1<!>
        return <!INVALID_LAW!>n<!> > 0
    }
}

interface RefinedLaws : Laws {
    <!INVALID_LAW, INVALID_OBSERVER!>@Pure override fun accepted(): Boolean = true<!>
}

interface RefinedAgain : RefinedLaws {
    <!INVALID_LAW, INVALID_OBSERVER!>@Pure override fun accepted(): Boolean = false<!>
}

interface SimpleLaw {
    @Law @Pure fun truth(): Boolean = true
}

class InheritedLaw : SimpleLaw

class ExplicitOverride : SimpleLaw {
    <!INVALID_LAW!>override fun truth(): Boolean = false<!>
}

<!INVALID_LAW!>class DelegatedLaw(inner: SimpleLaw) : SimpleLaw by inner<!>

open class BaseLaw {
    open fun truth(): Boolean = false
}

<!INVALID_LAW!><!MANY_IMPL_MEMBER_NOT_IMPLEMENTED!>class SuperclassLaw<!> : BaseLaw(), SimpleLaw<!>

interface ObserverBase {
    @Pure fun count(): Int
}

interface DefaultObserver : ObserverBase {
    @Pure override fun count(): Int = 1
    @Law @Pure fun defaultObserver(): Boolean = count() > 0
}
