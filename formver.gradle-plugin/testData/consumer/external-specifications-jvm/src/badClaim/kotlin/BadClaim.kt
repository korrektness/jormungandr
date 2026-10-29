import org.jetbrains.kotlin.formver.plugin.*
import stand.lib.*
import kotlin.math.abs

fun wrongDouble() {
    verify(twice(4) == 9)
}

fun unguardedAbs(x: Int): Int = abs(x)

fun negativeCreate(): Int = create(-1).value()

fun wrongListElement() {
    val list = listOf(7)
    verify(list[0] == 8)
}

@Deprecated("replaced")
fun retired(): Int = 0

fun callsRetired(): Int = retired()
