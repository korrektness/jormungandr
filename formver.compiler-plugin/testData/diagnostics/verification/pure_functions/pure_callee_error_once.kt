// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Box(var content: Int)

// Each caller's program embeds this body; its well-formedness error is reported once, not once per caller.
<!VIPER_VERIFICATION_ERROR!>@Pure
fun <!VERIFICATION_SKIPPED!>readShared<!>(b: Box): Int = <!UNSUPPORTED_OWNERSHIP!>b.content<!><!>

fun <!VIPER_TEXT!>firstCaller<!>(b: Box): Int = readShared(b)

fun <!VIPER_TEXT!>secondCaller<!>(b: Box): Int = readShared(b)

fun <!VIPER_TEXT!>thirdCaller<!>(b: Box): Int = readShared(b)
