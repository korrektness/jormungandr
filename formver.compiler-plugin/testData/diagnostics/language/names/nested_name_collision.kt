// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Collision<!> {
    <!VIPER_TEXT!>class Part<!>(val value: Int)
}

<!VIPER_TEXT!>class Collision_Part<!>(val value: Int)

fun <!VIPER_TEXT!>distinctClassNames<!>(nested: Collision.Part, underscore: Collision_Part): Boolean {
    postconditions<Boolean> { it == (nested.value == underscore.value) }
    return nested.value == underscore.value
}
