package external.models

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Counter")
interface CounterModel {
    @Pure @Unique fun value(): Int
    @Pure @get:Unique val label: Int

    @Law @Pure @Unique fun nonnegative(): Boolean = value() >= 0
    @Law @Pure @Unique fun labelValue(): Boolean = label == value()

    @Unique @Borrowed fun advance(step: Int): Int

    @SpecOf("advance")
    @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step >= 0; value() <= 2147483647 - step }
        postconditions<Int> { result ->
            result == old(value()) + step
            value() == result
        }
    }

    @ExternalMember("bounded")
    @Unique @Borrowed fun cap(limit: Int): Int

    @SpecOf("cap")
    @Unique @Borrowed fun capSpec(limit: Int) {
        postconditions<Int> { result -> result <= limit }
    }
}

@ExternalSpec("external.library.Cell")
interface CellModel<T> {
    @Pure @Unique fun get(): T
    @Unique @Borrowed fun set(value: T)
    @SpecOf("set") @Unique @Borrowed fun setSpec(value: T) {
        postconditions<Unit> { get() == value }
    }
}

@ExternalSpec("external.library.Gauge")
interface GaugeModel {
    @Pure @Unique fun read(): Int
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}

@ExternalFunction("external.library.create")
fun createModel(initial: Int): @Unique CounterModel = TODO()

@SpecOf("createModel")
fun createSpec(initial: Int) {
    preconditions { initial >= 0 }
    postconditions<@Unique CounterModel> { result -> result.value() == initial }
}

@ExternalFunction("external.library.twice")
@Pure fun twiceModel(x: Int): Int = 0

@SpecOf("twiceModel")
fun twiceSpec(x: Int) {
    preconditions { x >= -1073741824; x <= 1073741823 }
    postconditions<Int> { result -> result == x * 2 }
}

@ExternalFunction("external.library.shift")
fun @Unique @Borrowed CounterModel.shiftModel(amount: Int): Int = 0

@SpecOf("shiftModel")
fun @Unique @Borrowed CounterModel.shiftSpec(amount: Int) {
    preconditions { amount >= 0; value() <= 2147483647 - amount }
    postconditions<Int> { result ->
        result == old(value()) + amount
        value() == result
    }
}

@ExternalFunction("external.library.identity")
@Pure fun <T> identityModel(x: T): T = x

@SpecOf("identityModel")
fun <T> identitySpec(x: T) {
    postconditions<T> { result -> result == x }
}
