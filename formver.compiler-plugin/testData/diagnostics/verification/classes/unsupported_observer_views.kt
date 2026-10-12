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
<!INVALID_OBSERVER!>class InheritedTally : TallyBase(0), Tally<!>
<!UNSUPPORTED_FEATURE!>fun useInherited(t: @Unique InheritedTally): @Unique Tally = t<!>

interface SizedProperty {
    <!INVALID_OBSERVER!>@Pure val size: Int<!>
    @Law @Pure fun nonnegative(): Boolean = size >= 0
}
<!UNSUPPORTED_FEATURE!>class FixedProperty : SizedProperty {
    <!INVALID_OBSERVER!>@Pure override val size: Int get() = 3<!>
}<!>
<!UNSUPPORTED_FEATURE!>fun propertyClient(s: @Unique @Borrowed SizedProperty) { verify(<!UNSUPPORTED_FEATURE!>s.size<!> == 3) }<!>
