// LOCALITY_CHECK_ONLY
// LANGUAGE: +ContextParameters

import org.jetbrains.kotlin.formver.plugin.Borrowed

class Value

fun share(value: Value) {}
context(_: Value)
fun shareContext() {}

fun rejectLocalArgument(value: @Borrowed Value) {
    share(<!LOCALITY_MISMATCH!>value<!>)
}

context(_: @Borrowed Value)
fun rejectLocalContext() {
    <!CONTEXT_LOCALITY_MISMATCH!>shareContext()<!>
}

fun requireBorrowingFunction(function: (@Borrowed Value) -> Unit) {}
context(_: (@Borrowed Value) -> Unit)
fun requireBorrowingFunctionContext() {}

fun rejectGlobalContract(function: (Value) -> Unit) {
    requireBorrowingFunction(<!LOCALITY_CONTRACT_MISMATCH!>function<!>)
}

context(_: (Value) -> Unit)
fun rejectGlobalContextContract() {
    <!CONTEXT_LOCALITY_CONTRACT_MISMATCH!>requireBorrowingFunctionContext()<!>
}

fun rejectClassCapture(value: @Borrowed Value) {
    class Boundary {
        fun use() {
            val copy = <!INVALID_LOCALITY_CAPTURE!>value<!>
        }
    }
}
