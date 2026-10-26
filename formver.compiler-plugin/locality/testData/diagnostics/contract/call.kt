// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed


fun requireLocalFunction(f: (@Borrowed Any) -> Unit) {}

fun requireGlobalFunction(f: (Any) -> Unit) {}

fun requireTwoLocalFunctions(f: (@Borrowed Any) -> Unit, g: (@Borrowed Any) -> Unit) {}

fun requireLocalLocalFunction(f: (@Borrowed Any, @Borrowed Any) -> Unit) {}

fun requireLocalReceiverFunction(f: (@Borrowed Any).() -> Unit) {}

fun ((@Borrowed Any) -> Unit).requireLocalFunctionReceiver() {}

fun produceLocalFunction(): (@Borrowed Any) -> Unit =
    { _: @Borrowed Any -> Unit }

fun produceGlobalFunction(): (Any) -> Unit =
    { _: Any -> Unit }

fun produceGlobalLocalFunction(): (Any, @Borrowed Any) -> Unit =
    { _: Any, _: @Borrowed Any -> Unit }

fun produceGlobalReceiverFunction(): (Any).() -> Unit =
    { Unit }

fun Any.localCallback(): (@Borrowed Any) -> Unit = produceLocalFunction()

fun Any.globalCallback(): (Any) -> Unit = produceGlobalFunction()

fun `safe calls preserve callback contracts`(receiver: Any?, value: @Borrowed Any) {
    val local: ((@Borrowed Any) -> Unit)? = receiver?.localCallback()
    val global: ((@Borrowed Any) -> Unit)? = <!LOCALITY_CONTRACT_MISMATCH!>receiver?.globalCallback()<!>
    local?.invoke(value)
}

fun `safe call with fallback preserves callback contract`(receiver: Any?) {
    val local: (@Borrowed Any) -> Unit = receiver?.localCallback() ?: produceLocalFunction()
    val global: (@Borrowed Any) -> Unit = <!LOCALITY_CONTRACT_MISMATCH!>receiver?.globalCallback() ?: produceLocalFunction()<!>
}

fun `pass global function as local function argument`() {
    requireLocalFunction(<!LOCALITY_CONTRACT_MISMATCH!>produceGlobalFunction()<!>)
}

fun `pass global function as named local function argument`() {
    requireLocalFunction(f = <!LOCALITY_CONTRACT_MISMATCH!>produceGlobalFunction()<!>)
}

fun `pass global function as second local function argument`() {
    requireTwoLocalFunctions(produceLocalFunction(), <!LOCALITY_CONTRACT_MISMATCH!>produceGlobalFunction()<!>)
}

fun `pass global-local function as local-local function argument`() {
    requireLocalLocalFunction(<!LOCALITY_CONTRACT_MISMATCH!>produceGlobalLocalFunction()<!>)
}

fun `pass local function as global function argument`() {
    requireGlobalFunction(produceLocalFunction())
}

fun `pass global receiver function as local receiver function`() {
    requireLocalReceiverFunction(<!LOCALITY_CONTRACT_MISMATCH!>produceGlobalReceiverFunction()<!>)
}

fun `pass global function as local function receiver`() {
    <!LOCALITY_CONTRACT_MISMATCH!>produceGlobalFunction()<!>.requireLocalFunctionReceiver()
}

fun consumeBorrowingCallback(callback: (@Borrowed Any) -> Unit) {}
fun consumeSharingCallback(callback: (Any) -> Unit) {}

fun `nested callback parameters are contravariant`() {
    val unsafe: ((Any) -> Unit) -> Unit = <!LOCALITY_CONTRACT_MISMATCH!>::consumeBorrowingCallback<!>
    val safe: ((@Borrowed Any) -> Unit) -> Unit = ::consumeSharingCallback
}

fun `joining consumers intersects their callback contracts`(flag: Boolean) {
    val consumer = if (flag) ::consumeBorrowingCallback else ::consumeSharingCallback
    val unsafe: ((Any) -> Unit) -> Unit = <!LOCALITY_CONTRACT_MISMATCH!>consumer<!>
    val safe: ((@Borrowed Any) -> Unit) -> Unit = consumer
}
