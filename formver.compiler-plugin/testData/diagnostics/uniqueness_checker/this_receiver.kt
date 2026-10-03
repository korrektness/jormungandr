// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique

class Node(var value: Int)

fun consume(n: @Unique Node) {}

fun share(c: Any) {}

open class Counter(var count: Int, var node: @Unique Node) {
    @Unique @Borrowed
    fun borrow() {
        count++
    }

    @Unique
    fun consumeThis() {}

    @Borrowed
    fun peek(): Int = count

    @Unique @Pure
    fun pureRead(): Int = count

    fun plain() {}

    @Unique @Borrowed
    fun `calls borrowing members`() {
        borrow()
        this.borrow()
        peek()
        pureRead()
        count = node.value
    }

    @Unique @Borrowed
    fun `moves owned field`() {
        consume(node)
        node = Node(0)
    }

    @Unique @Borrowed
    fun `leaves owned field moved`() <!EXIT_UNIQUENESS_INCONSISTENCY!>{
        consume(node)
    }<!>

    @Unique @Borrowed
    fun `calls unannotated member`() <!EXIT_UNIQUENESS_INCONSISTENCY!>{
        plain()
    }<!>

    @Unique @Borrowed
    fun `calls consuming member`() <!EXIT_UNIQUENESS_INCONSISTENCY!>{
        <!LOCALITY_MISMATCH!>consumeThis()<!>
    }<!>

    @Unique
    fun `consumes this`() {
        plain()
    }

    @Unique
    fun `uses this after consuming it`(): Int {
        consumeThis()
        return <!INVALID_MOVED_ACCESS!>count<!>
    }

    @Borrowed
    fun `returns this`(): Counter {
        <!EXIT_UNIQUENESS_INCONSISTENCY!>return <!LOCALITY_MISMATCH!>this<!><!>
    }

    @Borrowed
    fun `stores this`() <!EXIT_UNIQUENESS_INCONSISTENCY!>{
        share(<!LOCALITY_MISMATCH!>this<!>)
    }<!>

    @Borrowed
    fun `consumes borrowed this`() <!EXIT_UNIQUENESS_INCONSISTENCY!>{
        <!LOCALITY_MISMATCH, UNIQUENESS_MISMATCH!>consumeThis()<!>
    }<!>

    fun `shared this calls consuming member`() {
        <!UNIQUENESS_MISMATCH!>consumeThis()<!>
    }

    @Unique
    fun `owned this into unique local`(): Int {
        val c: @Unique Counter = this
        return c.count
    }

    fun `shared this into unique local`() {
        val c: @Unique Counter = <!UNIQUENESS_MISMATCH!>this<!>
    }

    @Unique @Borrowed
    fun `passes this twice`() {
        <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>take(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>this<!>)<!>
    }

    @Unique @Borrowed
    fun take(c: @Unique @Borrowed Counter) {}

    @Unique @Borrowed
    fun `captures this`(): () -> Int {
        return <!INVALID_UNIQUENESS_CAPTURE!>{ count }<!>
    }

    @Unique @Borrowed
    fun `inline lambda reads this`() {
        repeat(2) { count++ }
    }

    open fun open() {}
}

class Sub(count: Int, node: @Unique Node) : Counter(count, node) {
    <!OVERRIDE_UNIQUENESS_MISMATCH!>@Unique override fun open() {}<!>
}

object Registry {
    var size = 0

    @Unique @Borrowed
    fun grow() {
        size++
    }
}

<!INVALID_UNIQUENESS_TYPE_TARGET!>@Unique<!>
fun topLevel() {}

fun `local function`() {
    <!INVALID_UNIQUENESS_TYPE_TARGET!>@Unique<!>
    fun local() {}
}
