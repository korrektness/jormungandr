// LOCALITY_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed

val globalFunction: (Any) -> Unit = { _: Any -> Unit }

class Handlers {
    var borrowing: ((@Borrowed Any) -> Unit)? = null

    fun `assign a global function`() {
        borrowing = <!LOCALITY_CONTRACT_MISMATCH!>globalFunction<!>
    }

    fun `assign null or a global function`(b: Boolean) {
        borrowing = <!LOCALITY_CONTRACT_MISMATCH!>if (b) null else globalFunction<!>
    }
}
