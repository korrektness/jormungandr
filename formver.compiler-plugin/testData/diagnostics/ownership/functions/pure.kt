// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique

class Node(val value: Int) {
    var next: @Unique Node? = null
    val longValue: Long = 0L
    val shortValue: Short = 0
    val byteValue: Byte = 0
}

@Pure
fun alias(n: @Unique Node): <!INVALID_PURE_REFERENCE_RESULT!>Node<!> = <!EXIT_UNIQUENESS_INCONSISTENCY, LOCALITY_MISMATCH!>n<!>

@Pure
fun aliasNext(n: @Unique Node): <!INVALID_PURE_REFERENCE_RESULT!>Node?<!> = <!EXIT_UNIQUENESS_INCONSISTENCY!>n.next<!>

@Pure
fun fresh(): <!INVALID_PURE_UNIQUE_RESULT!>@Unique Node<!> = Node(0)

@Pure
fun tail(n: @Unique Node): <!INVALID_PURE_REFERENCE_RESULT!>Node?<!> {
    val m = n.next
    return m
}

@Pure
fun @Unique Node.self(): <!INVALID_PURE_REFERENCE_RESULT!>Any<!> = 0

@Pure
fun value(n: @Unique Node): Int = n.value

@Pure
fun nextValue(n: @Unique Node): Int? = n.next?.value

@Pure
fun hasNext(n: @Unique Node): Boolean = n.next != null

@Pure
fun describe(n: @Unique Node): String = "node"

@Pure
fun shareNext(n: Node): Node? = n.next

@Pure
fun longValue(n: @Unique Node): Long = n.longValue

@Pure
fun shortValue(n: @Unique Node): Short = n.shortValue

@Pure
fun byteValue(n: @Unique Node): Byte = n.byteValue
