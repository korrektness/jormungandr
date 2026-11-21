// UNIQUE_CHECK_ONLY
// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.Unique

class Names : Iterable<String> {
    override fun iterator(): Iterator<String> = listOf("a").iterator()
}

fun consume(names: @Unique Iterable<String>) {}

fun `annotated local of a source implementation`() {
    val names: @Unique Names = <!UNIQUENESS_MISMATCH!>Names()<!>
}

fun `source implementation passed to an owning parameter`() {
    consume(<!UNIQUENESS_MISMATCH!>Names()<!>)
}

class Key

class Registry : Map<Key, Int> {
    override val size: Int get() = 0
    override val keys: Set<Key> get() = emptySet()
    override val values: Collection<Int> get() = emptyList()
    override val entries: Set<Map.Entry<Key, Int>> get() = emptySet()
    override fun isEmpty(): Boolean = true
    override fun containsKey(key: Key): Boolean = false
    override fun containsValue(value: Int): Boolean = false
    override fun get(key: <!OVERRIDE_UNIQUENESS_MISMATCH!>@Unique Key<!>): Int? = null
}
