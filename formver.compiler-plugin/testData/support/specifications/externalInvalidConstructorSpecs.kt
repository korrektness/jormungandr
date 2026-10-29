package external.invalid.constructors

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Counter")
interface CounterModel { @Pure @Unique fun value(): Int }
@ExternalSpec("external.library.Cell")
interface CellModel<T> { @Pure @Unique fun get(): T }

@ExternalConstructor("external.library.Counter")
fun nullable(initial: Int): CounterModel? = TODO()
@ExternalConstructor("external.library.Counter")
fun otherClass(initial: Int): CellModel<Int> = TODO()
@ExternalConstructor("external.library.Counter")
fun noMatch(initial: Boolean): CounterModel = TODO()
@ExternalConstructor("external.library.Counter")
fun first(initial: Int): CounterModel = TODO()
@ExternalConstructor("external.library.Counter")
fun duplicate(initial: Int): CounterModel = TODO()
@ExternalConstructor("external.library.Cell")
fun <T : Number> badBounds(initial: T): CellModel<T> = TODO()
@ExternalConstructor("external.library.Cell")
fun <T> fixedArgument(initial: T): CellModel<Int> = TODO()
@ExternalConstructor("external.library.Cell")
fun <T> nullableArgument(initial: T): CellModel<T?> = TODO()
@ExternalConstructor("external.library.DefaultGauge")
fun unbound(): CounterModel = TODO()
@ExternalConstructor("missing.ConstructorTarget")
fun missing(): CounterModel = TODO()
@ExternalConstructor("external.library.Counter")
fun CounterModel.extension(initial: Int): CounterModel = TODO()
