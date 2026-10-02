// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed

object Registry {
    var last: Any? = null
}

fun interface Getter {
    fun get(): Any?
}

fun store(x: Any) {}

fun borrow(x: @Borrowed Any) {}

fun Any.storeReceiver() {}

class `reject store of this in init` {
    init {
        Registry.last = <!LOCALITY_MISMATCH!>this<!>
    }
}

class `reject store of this in constructor` {
    constructor() {
        store(<!LOCALITY_MISMATCH!>this<!>)
    }
}

class `reject this in property initializer` {
    val self: Any = <!LOCALITY_MISMATCH!>this<!>
}

class `reject this as extension receiver in init` {
    init {
        <!LOCALITY_MISMATCH!>this<!>.storeReceiver()
    }
}

class `reject member call in init` {
    fun leak() {
        Registry.last = this
    }

    init {
        <!INVALID_CONSTRUCTION_RECEIVER!>leak()<!>
        <!INVALID_CONSTRUCTION_RECEIVER!>this.leak()<!>
    }
}

class `reject custom accessor in init` {
    val custom: Any get() = this

    init {
        val x = <!INVALID_CONSTRUCTION_RECEIVER!>custom<!>
    }
}

class `reject stored lambda capturing this in init` {
    var count: Int = 0

    init {
        val f = { count }
        Registry.last = <!LOCALITY_MISMATCH!>f<!>
    }
}

class `reject anonymous object capturing this in init` {
    var count: Int = 0

    init {
        Registry.last = object : Getter {
            override fun get(): Any? = <!INVALID_LOCALITY_CAPTURE!>count<!>
        }
    }
}

class `accept fields, borrows and local lambdas in init` {
    var count: Int = 0
    var other: Any? = null

    init {
        count = 1
        val c = count
        other = Any()
        borrow(this)
        val f = { count }
        f()
    }
}

class `accept this outside construction` {
    val self: Any get() = this

    fun leak() {
        Registry.last = this
    }
}
