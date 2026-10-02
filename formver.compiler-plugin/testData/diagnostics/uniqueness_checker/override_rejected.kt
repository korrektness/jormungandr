// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Node

open class Base {
    open fun consume(node: @Unique Node) {}
    open fun inspect(node: @Unique @Borrowed Node) {}
    open fun share(node: Node) {}
    open fun make(): @Unique Node = Node()
    open fun @Unique Node.extension() {}
    open val property: @Unique Node = Node()
}

class Derived : Base() {
    override fun consume(node: <!OVERRIDE_UNIQUENESS_MISMATCH!>Node<!>) {}
    override fun inspect(node: <!OVERRIDE_UNIQUENESS_MISMATCH!>@Unique Node<!>) {}
    override fun share(node: <!OVERRIDE_UNIQUENESS_MISMATCH!>@Borrowed Node<!>) {}
    override fun make(): <!OVERRIDE_UNIQUENESS_MISMATCH!>Node<!> = Node()
    override fun <!OVERRIDE_UNIQUENESS_MISMATCH!>Node<!>.extension() {}
    override val property: <!OVERRIDE_UNIQUENESS_MISMATCH!>Node<!> = Node()
}

interface Source {
    fun take(): @Unique Node
}

class NodeSource : Source {
    <!OVERRIDE_UNIQUENESS_MISMATCH!>override fun take() = Node()<!>
}
