// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

interface Family
<!VIPER_TEXT!>class Representation<!>(var value: Int) : Family

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>downcast<!>(value: @Unique Family): @Unique Representation {
    <!UNSUPPORTED_OWNERSHIP!>return value as Representation<!>
}
