// WITH_STDLIB

import kotlin.properties.Delegates

open <!VERIFICATION_SKIPPED, VERIFICATION_SKIPPED!>class Holder<!>(x: Int) {
    private var <!VIPER_TEXT, VIPER_TEXT!>count<!>: Int by <!OPAQUE_CALL, OPAQUE_CALL, OPAQUE_CALL, OPAQUE_CALL!>Delegates.notNull()<!>

    constructor(x: Int, y: Int) : this(x) {
        bump()
    }

    fun <!VIPER_TEXT!>bump<!>(): Int {
        count = 0
        return count
    }
}
