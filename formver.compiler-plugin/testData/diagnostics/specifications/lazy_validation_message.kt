// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>checkedLimit<!>(input: String?, parsed: Int?): Int {
    val setting = input ?: return 4
    require(parsed != null && parsed in 1..16) { "Invalid limit: '$setting'" }
    return parsed
}

fun <!VIPER_TEXT!>forbidsUpperBoundRejection<!>(input: String?, parsed: Int?): Int {
    throwsOnlyIf { input != null && (parsed == null || parsed < 1) }
    val setting = input ?: return 4
    <!THROWS_CONDITION_NOT_ESTABLISHED!>require(parsed != null && parsed in 1..16) { "Invalid limit: '$setting'" }<!>
    return parsed
}
