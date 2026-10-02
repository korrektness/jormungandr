import org.jetbrains.kotlin.formver.plugin.*

fun anonymousFunction(): Int {
    val y = 1
    val f = <!INTERNAL_ERROR!>fun(x: Int): Int { return x }<!>
    return y
}
