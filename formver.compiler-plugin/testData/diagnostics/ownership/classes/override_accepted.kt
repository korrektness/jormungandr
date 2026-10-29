// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Node

open class Base {
    open fun consume(node: @Unique Node) {}
    open fun inspect(node: @Unique @Borrowed Node) {}
    open fun look(node: @Borrowed Node) {}
    open fun share(node: Node) {}
    open fun make(): @Unique Node = Node()
    open fun @Unique Node.extension() {}
    open val property: @Unique Node = Node()
}

class Derived : Base() {
    override fun consume(node: @Unique Node) {}
    override fun inspect(node: @Unique @Borrowed Node) {}
    override fun look(node: @Borrowed Node) {}
    override fun share(node: Node) {}
    override fun make(): @Unique Node = Node()
    override fun @Unique Node.extension() {}
    override val property: @Unique Node = Node()
}

interface Source {
    fun take(): @Unique Node
}

class NodeSource : Source {
    override fun take(): @Unique Node = Node()
}
