// UNIQUE_CHECK_ONLY
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

object Registry {
    var last: Any? = null
}

fun interface Getter {
    fun get(): Any?
}

fun store(x: Any) {}

fun borrow(x: @Borrowed Any) {}

fun Any.storeReceiver() {}

fun consume(x: @Unique Any) {}

class StoresThisInInit {
    init {
        Registry.last = this
    }
}

class PassesThisInConstructor {
    constructor() {
        store(this)
    }
}

class StoresThisInInitializer {
    val self: Any = this
}

class CallsExtensionOnThis {
    init {
        this.storeReceiver()
    }
}

class CallsMember {
    fun leak() {
        Registry.last = this
    }

    init {
        leak()
    }
}

class ReadsCustomAccessor {
    val custom: Any get() = this

    init {
        val x = custom
    }
}

open class ReadsOpenProperty {
    open val size: Int = 0
    val doubled: Int = size * 2
}

class StoresLambda {
    var count: Int = 0

    init {
        val f = { count }
        Registry.last = f
    }
}

class StoresAnonymousObject {
    var count: Int = 0

    init {
        Registry.last = object : Getter {
            override fun get(): Any? = count
        }
    }
}

class InheritsEscape : CallsExtensionOnThisBase()

open class CallsExtensionOnThisBase {
    init {
        storeReceiver()
    }
}

class UsesFields {
    var count: Int = 0
    var other: Any? = null

    init {
        count = 1
        val c = count
        other = Any()
    }
}

class BorrowsThis {
    init {
        borrow(this)
    }
}

class RunsInPlace {
    var count: Int = 0

    init {
        repeat(2) { count += 1 }
    }
}

abstract class Sized {
    abstract val size: Int
}

class ReadsOverriddenProperty(override val size: Int) : Sized() {
    val tripled: Int = this.size * 3
}

class EscapesOutsideConstruction {
    val self: Any get() = this

    fun leak() {
        Registry.last = this
    }
}

fun `reject escaping constructions where uniqueness is required`() {
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>StoresThisInInit()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>PassesThisInConstructor()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>StoresThisInInitializer()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>CallsExtensionOnThis()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>CallsMember()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>ReadsCustomAccessor()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>ReadsOpenProperty()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>StoresLambda()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>StoresAnonymousObject()<!>)
    consume(<!SHARED_CONSTRUCTION_MISMATCH!>InheritsEscape()<!>)
    val local: @Unique Any = <!SHARED_CONSTRUCTION_MISMATCH!>CallsMember()<!>
}

fun `reject escaping construction as a unique result`(): @Unique Any = <!SHARED_CONSTRUCTION_MISMATCH!>CallsMember()<!>

fun `accept escaping constructions where sharing is enough`() {
    store(CallsMember())
    val shared = StoresThisInInit()
    store(shared)
}

fun `accept constructions that keep this`() {
    consume(UsesFields())
    consume(BorrowsThis())
    consume(RunsInPlace())
    consume(ReadsOverriddenProperty(1))
    consume(EscapesOutsideConstruction())
}
