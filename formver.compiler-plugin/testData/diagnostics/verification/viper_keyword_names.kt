// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>viperKeywordNames<!>(exhale: Int): Int {
    postconditions<Int> { res -> res == exhale + 2 }
    val acc = exhale + 1
    val unfolding = acc + 1
    return unfolding
}
