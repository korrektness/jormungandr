package stand.specs

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("stand.lib.Counter")
interface CounterModel {
    @Pure @Unique fun value(): Int

    @Law @Pure @Unique fun nonnegative(): Boolean = value() >= 0

    @Unique @Borrowed fun advance(step: Int): Int

    @SpecOf("advance")
    @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step >= 0; value() <= 2147483647 - step }
        postconditions<Int> { result ->
            result == old(value()) + step
            value() == result
        }
    }
}

@ExternalFunction("stand.lib.create")
fun createModel(initial: Int): @Unique CounterModel = TODO()

@SpecOf("createModel")
fun createSpec(initial: Int) {
    preconditions { initial >= 0 }
    postconditions<@Unique CounterModel> { result -> result.value() == initial }
}

@ExternalFunction("stand.lib.twice")
@Pure fun twiceModel(x: Int): Int = 0

@SpecOf("twiceModel")
fun twiceSpec(x: Int) {
    preconditions { -1073741824 <= x && x <= 1073741823 }
    postconditions<Int> { result -> result == x * 2 }
}
