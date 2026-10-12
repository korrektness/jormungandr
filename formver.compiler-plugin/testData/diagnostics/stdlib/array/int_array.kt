// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.verify

fun <!VIPER_TEXT!>createAndSize<!>() {
    val a = IntArray(3)
    verify(a.size == 3)
}
