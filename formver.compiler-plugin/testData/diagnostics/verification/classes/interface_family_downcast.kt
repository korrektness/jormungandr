// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

interface Family
class Representation(var value: Int) : Family

@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>downcast<!>(value: @Unique Family): @Unique Representation {
    <!UNSUPPORTED_OWNERSHIP!>return value as Representation<!>
}
