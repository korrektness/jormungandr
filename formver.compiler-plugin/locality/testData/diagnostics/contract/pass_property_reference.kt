// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed

class Box(val content: Any)

val Box.globalContent: Any
    get() = content

val (@Borrowed Box).localContent: Any
    get() = content

fun requireGlobalFunction(f: (Box) -> Any) {}

fun requireLocalFunction(f: (@Borrowed Box) -> Any) {}

inline fun <K, V> Iterable<K>.inlineAssociateWith(selector: (K) -> V): Map<K, V> =
    associateWith(selector)

fun `pass member property to requireGlobalFunction`() {
    requireGlobalFunction(Box::content)
}

fun `pass member property to requireLocalFunction`() {
    requireLocalFunction(<!LOCALITY_CONTRACT_MISMATCH!>Box::content<!>)
}

fun `pass globalContent to requireGlobalFunction`() {
    requireGlobalFunction(Box::globalContent)
}

fun `pass localContent to requireGlobalFunction`() {
    requireGlobalFunction(Box::localContent)
}

fun `pass globalContent to requireLocalFunction`() {
    requireLocalFunction(<!LOCALITY_CONTRACT_MISMATCH!>Box::globalContent<!>)
}

fun `pass localContent to requireLocalFunction`() {
    requireLocalFunction(Box::localContent)
}

fun `pass bound property to function without parameters`(box: Box) {
    run(box::content)
}

fun `pass member property to inline function`(boxes: List<Box>) {
    boxes.inlineAssociateWith(Box::content)
}
