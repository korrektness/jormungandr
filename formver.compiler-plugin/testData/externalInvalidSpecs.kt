package external.invalid

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("missing.Library")
interface MissingModel

@ExternalSpec("external.library.Counter")
interface WrongMemberModel {
    fun value(): Boolean
}

@ExternalSpec("external.library.Counter")
interface DuplicateClassModel

@ExternalFunction("external.library.twice")
fun first(x: Int): Int = 0

@ExternalFunction("external.library.twice")
fun second(x: Int): Int = 0

@ExternalFunction("external.library.twice")
fun wrongResult(x: Int): Boolean = false

// Rejected bounds leave the model unbound, so its unmatched member reports nothing of its own.
@ExternalSpec("external.library.Cell")
interface WrongBounds<T : Number> {
    fun missing(): T
}

@ExternalSpec("external.library.Gauge")
interface GaugeShapeModel {
    fun read(): Int
    class Nested
    // A property cannot stand for a function of the same name.
    @ExternalMember("read") val reading: Int
}

@ExternalMember("read")
fun strayMember(): Int = 0

@ExternalFunction("")
fun emptyTarget(): Int = 0

@ExternalSpec("LocalTarget")
interface SourceTargetModel
