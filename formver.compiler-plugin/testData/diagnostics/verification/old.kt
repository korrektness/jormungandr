// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*


class C(
    var field: @Unique Int
)

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>test<!>(c: @Unique @Borrowed C) {
    preconditions {
        c.field == 42
    }
    postconditions<Unit> {
        c.field == 43
    }
    inc(c)
    verify(c == old(<!LOCALITY_MISMATCH!>c<!>))
}

// TODO: Remove the @NeverConvert once we have uniqueness information.
@NeverConvert
fun inc(c: @Unique @Borrowed C) {
    postconditions<Unit> {
        c.field == old(c.field) + 1
    }
    c.field = <!UNIQUENESS_MISMATCH!>c.field + 1<!>
}
