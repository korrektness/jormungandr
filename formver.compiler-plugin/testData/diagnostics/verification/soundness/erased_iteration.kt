// FULL_JDK
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

// Each element of an iteration snapshot has the substituted element type (docs/soundness.md, "Type invariants").
// The list holds a `String`, which the JVM never checks against `Int`.
// UNSOUND marks an assertion that is false at runtime but verifies.

// UNSOUND: the element `forEach` passes inhales the substituted type `Int`.
fun <!VIPER_TEXT!>castListIteration<!>() {
    @Suppress("UNCHECKED_CAST")
    val ints: @Unique List<Int> = listOf<Any>("a") as List<Int>
    ints.forEach {
        val x: Any = it
        verify(x is Int)
    }
}
