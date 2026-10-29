// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

fun consume(sb: @Unique StringBuilder) {}

fun `string builder constructor is unique`() {
    val sb: @Unique StringBuilder = StringBuilder()
    consume(sb)
}

fun `intrinsics do not move the builder`(sb: @Unique @Borrowed StringBuilder, s: String) {
    sb.append('a')
    sb.append(s)
    val n = sb.length
    val t = sb.toString()
    sb.clear()
}

fun `a chain does not move the builder`(sb: @Unique @Borrowed StringBuilder) {
    sb.append('a').append("b").clear().append('c')
}

fun `appending then consuming`() {
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append('a')
    consume(sb)
}

fun `passing the result of append moves the builder`() {
    val sb: @Unique StringBuilder = StringBuilder()
    consume(sb.append('a'))
    <!INVALID_MOVED_ACCESS!>sb<!>.append('b')
}

fun `binding the result of append moves the builder`() {
    val sb: @Unique StringBuilder = StringBuilder()
    val t = sb.append('x')
    t.append('y')
    val n = <!INVALID_MOVED_ACCESS!>sb<!>.length
}

fun `returning the result of append`(sb: @Unique StringBuilder): @Unique StringBuilder {
    return sb.append('a')
}

fun `appending to a moved builder is a moved access`() {
    val sb: @Unique StringBuilder = StringBuilder()
    consume(sb)
    <!INVALID_MOVED_ACCESS!>sb<!>.append('a')
}

fun `other members consume the builder`() {
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append(1)
    val n = <!INVALID_MOVED_ACCESS!>sb<!>.length
}

fun `appending a nullable string consumes the builder`(s: String?) {
    val sb: @Unique StringBuilder = StringBuilder()
    sb.append(s)
    <!INVALID_MOVED_ACCESS!>sb<!>.clear()
}

fun `returning append on a borrowed builder`(sb: @Borrowed StringBuilder): StringBuilder {
    <!EXIT_UNIQUENESS_INCONSISTENCY!>return <!LOCALITY_MISMATCH!>sb.append('a')<!><!>
}
