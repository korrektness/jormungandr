// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>consume<!>(b: @Unique Box) {}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>useAfterMove<!>(b: @Unique Box): Int {
    consume(b)
    return <!INVALID_MOVED_ACCESS!>b<!>.value
}

@AlwaysVerify
fun <!VIPER_TEXT!>noUniquenessErrors<!>(b: @Unique Box) {
    consume(b)
}
