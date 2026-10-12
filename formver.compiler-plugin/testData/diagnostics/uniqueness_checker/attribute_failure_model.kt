// UNIQUE_CHECK_ONLY
// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Value

fun consume(value: @Unique Value) {}
fun borrow(value: @Borrowed Value) {}
fun consumePair(first: @Unique Value, second: @Unique Value) {}

fun rejectMovedAccess(value: @Unique Value) {
    consume(value)
    borrow(<!INVALID_MOVED_ACCESS!>value<!>)
}

fun rejectCollision(value: @Unique Value) {
    consumePair(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>value<!>, <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>value<!>)
}

fun rejectCapture(value: @Unique Value) {
    val callback = <!INVALID_UNIQUENESS_CAPTURE!>{ borrow(value) }<!>
}

object Registry {
    var value: Any? = null
}

class Escapes {
    init {
        Registry.value = this
    }
}

fun consumeEscape(value: @Unique Escapes) {}

fun rejectConstruction() {
    consumeEscape(<!SHARED_CONSTRUCTION_MISMATCH!>Escapes()<!>)
}
