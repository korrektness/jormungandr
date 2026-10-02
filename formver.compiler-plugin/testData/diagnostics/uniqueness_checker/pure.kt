// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique

class Node(val value: Int) {
    var next: @Unique Node? = null
}

@Pure
fun alias(n: @Unique Node): Node = <!EXIT_UNIQUENESS_INCONSISTENCY, LOCALITY_MISMATCH!>n<!>

@Pure
fun aliasNext(n: @Unique Node): Node? = <!EXIT_UNIQUENESS_INCONSISTENCY!>n.next<!>

@Pure
fun fresh(): <!INVALID_PURE_UNIQUE_RESULT!>@Unique Node<!> = Node(0)
