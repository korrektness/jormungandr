package external.constructors

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Counter")
interface CounterModel {
    @Pure @Unique fun value(): Int
    @Unique @Borrowed fun advance(step: Int): Int
    @SpecOf("advance") @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step >= 0; value() <= 2147483647 - step }
        postconditions<Int> { result -> result == old(value()) + step; value() == result }
    }
}

@ExternalConstructor("external.library.Counter")
fun counter(initial: Int): @Unique CounterModel = TODO()
@SpecOf("counter") fun counterSpec(initial: Int) {
    preconditions { initial >= 0 }
    postconditions<@Unique CounterModel> { result -> result.value() == initial }
}

@ExternalSpec("external.library.Cell")
interface CellModel<T> {
    @Pure @Unique fun get(): T
    @Unique @Borrowed fun set(value: T)
    @SpecOf("set") @Unique @Borrowed fun setSpec(value: T) {
        postconditions<Unit> { get() === value }
    }
}

@ExternalSpec("kotlin.collections.ArrayDeque")
interface DequeModel<T> {
    @Pure @get:Unique val size: Int
}

@ExternalConstructor("kotlin.collections.ArrayDeque")
fun <T> deque(): @Unique DequeModel<T> = TODO()
@SpecOf("deque") fun <T> dequeSpec() {
    postconditions<@Unique DequeModel<T>> { result -> result.size == 0 }
}

@ExternalConstructor("external.library.Cell")
fun <T> cell(initial: T): @Unique CellModel<T> = TODO()
@SpecOf("cell") fun <T> cellSpec(initial: T) {
    postconditions<@Unique CellModel<T>> { result -> result.get() === initial }
}
