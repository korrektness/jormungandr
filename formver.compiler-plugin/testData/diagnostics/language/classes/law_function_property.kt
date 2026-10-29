// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!UNSUPPORTED_FEATURE!>class LawPlainBox(var n: Int) {
    var hook: () -> Unit = {}
    init {
        require(n >= 0)
        hook = { n = -1 }
    }
    @Law @Pure @Unique fun invariant(): Boolean = n >= 0
}<!>

fun <!VIPER_TEXT!>lawPlainClient<!>(b: @Unique LawPlainBox) {
    b.hook()
    verify(b.n >= 0)
    refute(false)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>lawPlainCaller<!>() {
    lawPlainClient(<!SHARED_CONSTRUCTION_MISMATCH!>LawPlainBox(1)<!>)
}
