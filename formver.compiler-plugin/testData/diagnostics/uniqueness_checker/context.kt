// UNIQUE_CHECK_ONLY
// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Box {
    var item: @Unique Any = Any()
    var other: @Unique Any = Any()
}

context(box: @Borrowed Box)
fun borrowContext() {}

context(box: @Unique Box)
fun consumeContext() {}

context(box1: @Unique Box)
fun consumeContextAndArg(box2: @Unique Box) {}

context(box1: @Borrowed Box)
fun borrowContextAndConsumeArg(box2: @Unique Box) {}

context(box1: @Unique Box)
fun (@Unique Box).consumeContextAndReceiver() {}

context(box1: @Unique Box)
fun (@Unique Any).consumeContextAndItemReceiver() {}

context(box: Box)
fun shareContext() {}

context(box: @Unique Box)
fun consumeContextItem() {
    consume(box.item)
}

context(box: @Unique Box)
val uniqueContextProperty: Any
    get() = Any()

context(box: @Borrowed Box)
val borrowedContextProperty: Any
    get() = Any()

context(box1: @Unique Box)
val @Unique Box.contextAndReceiverProperty: Any
    get() = Any()

context(box1: @Unique Box)
val @Unique Any.contextAndItemReceiverProperty: Any
    get() = Any()

context(box: @Unique Box)
var uniqueContextMutableProperty: Any
    get() = Any()
    set(value) {}

context(box: @Borrowed Box)
var borrowedContextUniqueProperty: @Unique Any
    get() = Any()
    set(value) {}

fun consume(a: @Unique Any) {}

fun borrow(a: @Borrowed Any) {}

// Context call arguments

fun `borrowed context through with moves unique value`(box: @Unique Box) {
    with(box) {
        borrowContext()
    }

    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

fun `unique context consumes unique value`(box: @Unique Box) {
    with(box) {
        consumeContext()
    }

    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

fun `shared context consumes unique value`(box: @Unique Box) {
    with(box) {
        shareContext()
    }

    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

fun `borrowed context rejects moved value`(box: @Unique Box) {
    consume(box)

    with(<!INVALID_MOVED_ACCESS!>box<!>) {
        borrowContext()
    }
}

// Context parameter bodies

context(box: @Unique Box)
fun `borrow context parameter then consume`() {
    borrow(box)
    consume(box)
}

context(box: @Unique Box)
fun `consume context parameter twice`() {
    consume(box)
    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

context(box: @Unique Box)
fun `consume context child then exit`() {
    consume(box.item)
}

context(box: @Unique Box)
fun `consume context child then restore`() {
    consume(box.item)
    box.item = Any()
}

// Context ownership across calls and accessors.

context(box: @Unique Box)
fun `borrow context then consume`() {
    borrowContext()
    consume(box)
}

context(box: @Unique Box)
fun `borrow context property then consume`() {
    val x = borrowedContextProperty
    consume(box)
}

context(box: @Unique Box)
fun `context property consumes its argument`() {
    val x = uniqueContextProperty
    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

context(box: @Unique Box)
fun `context setter consumes its argument`() {
    uniqueContextMutableProperty = Any()
    consume(<!INVALID_MOVED_ACCESS!>box<!>)
}

context(box: @Unique Box)
fun `context calls reject moved implicit argument`() {
    consumeContext()
    <!INVALID_MOVED_ACCESS!>consumeContext()<!>
}

context(box: @Unique Box)
fun `context properties reject moved implicit argument`() {
    val x = uniqueContextProperty
    val y = <!INVALID_MOVED_ACCESS!>uniqueContextProperty<!>
}

context(box: @Unique Box)
fun `context setter rejects moved implicit argument`() {
    consume(box)
    <!INVALID_MOVED_ACCESS!>uniqueContextMutableProperty<!> = Any()
}

context(box: @Unique Box)
fun `context getter rejects inconsistent argument`() {
    consume(box.item)
    val x = <!CONTEXT_ESCAPE_UNIQUENESS_INCONSISTENCY!>uniqueContextProperty<!>
}

context(box: @Unique Box)
fun `context setter rejects inconsistent argument`() {
    consume(box.item)
    <!CONTEXT_ESCAPE_UNIQUENESS_INCONSISTENCY!>uniqueContextMutableProperty = Any()<!>
}

context(box: @Unique Box)
fun `context setter value aliases context argument`() {
    <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>uniqueContextMutableProperty = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!><!>
}

context(box: @Unique Box)
fun `context setter value overlaps context argument`() {
    <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>uniqueContextMutableProperty = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>box.item<!><!>
}

context(box: @Unique Box)
fun `unique setter value aliases borrowed context`() {
    <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>borrowedContextUniqueProperty = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!><!>
}

context(box: @Unique Box)
fun `unique setter value is distinct from borrowed context`() {
    borrowedContextUniqueProperty = Any()
    consume(box)
}

fun later(block: () -> Unit) {}

context(box: @Unique Box)
fun `escaping lambda captures unique context`() {
    later <!INVALID_UNIQUENESS_CAPTURE!>{ borrow(box) }<!>
}

context(box: @Borrowed Box)
fun `escaping lambda captures borrowed context`() {
    later <!INVALID_UNIQUENESS_CAPTURE, LOCALITY_MISMATCH!>{ borrow(box) }<!>
}

context(box: Box)
fun `escaping lambda captures shared context`() {
    later { borrow(box) }
}

// Context parameter collisions

context(box: @Unique Box)
fun `consume as context and argument`() {
    <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>consumeContextAndArg(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!>)<!>
}

context(box: @Unique Box)
fun `borrow as context and consume as argument`() {
    <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>borrowContextAndConsumeArg(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!>)<!>
}

context(box: @Unique Box)
fun `borrow as context and consume another box as argument`(other: @Unique Box) {
    borrowContextAndConsumeArg(other)
}

context(box: @Unique Box)
fun `consume as context and receiver`() {
    <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!><!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!>.consumeContextAndReceiver()<!>
}

context(box: @Unique Box)
fun `consume as context and item receiver`() {
    <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!><!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>box.item<!>.consumeContextAndItemReceiver()<!>
}

context(box: @Unique Box)
fun `consume as context property only`() {
    val x = uniqueContextProperty
}

context(box: Box)
fun `unique context property rejects shared context`() {
    val x = <!CONTEXT_UNIQUENESS_MISMATCH!>uniqueContextProperty<!>
}

context(box: @Unique Box)
fun `borrowed context property accepts unique context`() {
    val x = borrowedContextProperty
}

context(box: @Unique Box)
fun `consume as context and property receiver`() {
    val x = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!><!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>box<!>.contextAndReceiverProperty<!>
}

context(box: @Unique Box)
fun `consume as context and item property receiver`() {
    val x = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!><!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>box.item<!>.contextAndItemReceiverProperty<!>
}
