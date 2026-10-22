package external.shared.constructors

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Cell")
interface CellModel<T> {
    @Pure @Unique fun get(): T
    @Unique @Borrowed fun set(value: T)
    @SpecOf("set") @Unique @Borrowed fun setSpec(value: T) {
        postconditions<Unit> { get() === value }
    }
}

@ExternalConstructor("external.library.Cell")
fun <T> sharedCell(initial: T): CellModel<T> = TODO()
