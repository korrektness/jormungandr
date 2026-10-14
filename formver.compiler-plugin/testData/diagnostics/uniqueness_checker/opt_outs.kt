// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.NeverVerify
import org.jetbrains.kotlin.formver.plugin.Unique

class Items {
    @Unique @Borrowed
    fun add(x: Int) {}
}

fun consume(a: @Unique Items) {}

fun share(a: Any) {}

// Opted-out code is trusted against its declared signature.

@NeverVerify
fun `never verify body`(items: Items, b: @Borrowed Any) {
    items.add(1)
    share(b)
}

@NeverConvert
fun `never convert body`(items: Items) {
    items.add(1)
    val f = { items.add(2) }
}

@NeverVerify
class OptedOut(val items: Items) {
    val first: Int = run { items.add(1); 0 }

    init {
        items.add(2)
    }

    fun member() {
        items.add(3)
    }

    class Nested(val items: Items) {
        fun member() {
            items.add(4)
        }
    }
}

class ConstructorOptedOut @NeverVerify constructor(val items: Items) {
    val first: Int = run { items.add(1); 0 }

    init {
        items.add(2)
    }

    val second: Int
        get() {
            <!UNIQUENESS_MISMATCH!>items<!>.add(3)
            return 0
        }

    fun member() {
        <!UNIQUENESS_MISMATCH!>items<!>.add(4)
    }
}

fun `unannotated body`(items: Items, b: @Borrowed Any) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    <!UNIQUENESS_MISMATCH!>items<!>.add(1)
    share(<!LOCALITY_MISMATCH!>b<!>)
}<!>

// Callers of opted-out declarations are checked against the declared signature.

@NeverVerify
fun takeUnique(a: @Unique Items) {}

@NeverVerify
fun borrowUnique(a: @Unique @Borrowed Items) {}

fun `caller passes shared to unique`(items: Items) {
    takeUnique(<!UNIQUENESS_MISMATCH!>items<!>)
}

fun `caller reuses moved argument`(items: @Unique Items) {
    takeUnique(items)
    consume(<!INVALID_MOVED_ACCESS!>items<!>)
}

fun `caller keeps borrowed argument`(items: @Unique Items) {
    borrowUnique(items)
    consume(items)
}
