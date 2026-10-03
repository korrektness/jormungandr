// UNIQUE_CHECK_ONLY
// LANGUAGE: +MultiPlatformProjects
// FULL_JDK

// MODULE: common
// FILE: common.kt

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Node

expect fun @Unique @Borrowed StringBuilder.appendCompat(codePoint: Int): StringBuilder
expect fun consume(node: @Unique Node)
expect fun inspect(node: @Unique @Borrowed Node)
expect fun share(node: Node)
expect fun make(): @Unique Node
expect fun matching(node: @Unique @Borrowed Node): @Unique Node

expect class Holder {
    fun take(node: @Unique Node)
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

actual fun <!ACTUAL_UNIQUENESS_MISMATCH!>StringBuilder<!>.appendCompat(codePoint: Int): StringBuilder = appendCodePoint(codePoint)
actual fun consume(node: <!ACTUAL_UNIQUENESS_MISMATCH!>Node<!>) {}
actual fun inspect(node: <!ACTUAL_UNIQUENESS_MISMATCH!>@Unique Node<!>) {}
actual fun share(node: <!ACTUAL_UNIQUENESS_MISMATCH!>@Borrowed Node<!>) {}
actual fun make(): <!ACTUAL_UNIQUENESS_MISMATCH!>Node<!> = Node()
actual fun matching(node: @Unique @Borrowed Node): @Unique Node = Node()

actual class Holder {
    actual fun take(node: <!ACTUAL_UNIQUENESS_MISMATCH!>Node<!>) {}
}
