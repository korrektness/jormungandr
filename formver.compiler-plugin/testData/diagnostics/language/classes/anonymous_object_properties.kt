import org.jetbrains.kotlin.formver.plugin.*

interface Named { val name: String }

fun <!VERIFICATION_SKIPPED!>makeNamed<!>(): Named = <!UNSUPPORTED_FEATURE!>object : Named<!> {
    override val name: String <!UNSUPPORTED_FEATURE!><!VERIFICATION_SKIPPED!>get<!>() = "a"<!>
}

fun <!VERIFICATION_SKIPPED!>makeStored<!>(): Named = <!UNSUPPORTED_FEATURE!>object : Named<!> {
    override val name: String = "b"
}
