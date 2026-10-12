// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Tally {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int
    @SpecOf("inc") @Unique @Borrowed fun incSpec() {
        postconditions<Unit> { count() == old(count()) + 1 }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>()
}
open class TallyBase(var v: Int) {
    @Pure @Unique fun <!VIPER_TEXT!>count<!>(): Int = v
    @Unique @Borrowed fun <!VIPER_TEXT!>inc<!>() {}
}
<!INVALID_OBSERVER, VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InheritedTally<!> : TallyBase(0), Tally<!>
<!UNSUPPORTED_FEATURE!>fun useInherited(t: @Unique InheritedTally): @Unique Tally = t<!>
