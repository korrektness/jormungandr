// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

interface Family
<!VIPER_TEXT!>class Representation<!>(var value: Int) : Family

<!UNSUPPORTED_OWNERSHIP!>fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>downcast<!>(value: @Unique Family): @Unique Representation {
    return value as Representation
}<!>
