package external.vacuous

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("external.library.Counter")
interface ContradictoryCounterModel {
    @Pure @Unique fun value(): Int

    @Law @Pure @Unique fun positive(): Boolean = value() > 0
    @Law @Pure @Unique fun negative(): Boolean = value() < 0

    @Unique @Borrowed fun advance(step: Int): Int

    @SpecOf("advance")
    @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step > 0; step < 0 }
    }

    @ExternalMember("bounded")
    @Unique @Borrowed fun cap(limit: Int): Int

    @SpecOf("cap")
    @Unique @Borrowed fun capSpec(limit: Int) {
        postconditions<Int> { result -> result < limit; result > limit }
    }
}

@ExternalSpec("external.library.Gauge")
interface ConsistentGaugeModel {
    @Pure @Unique fun read(): Int
    @Law @Pure @Unique fun nonnegative(): Boolean = read() >= 0
}

@ExternalSpec("external.library.DefaultGauge")
interface DefaultGaugeModel { @Pure @Unique fun read(): Int }

@ExternalConstructor("external.library.DefaultGauge")
fun gauge(): @Unique DefaultGaugeModel = TODO()
@SpecOf("gauge") fun gaugeSpec() {
    postconditions<@Unique DefaultGaugeModel> { false }
}

@ExternalFunction("external.library.twice")
@Pure fun twiceModel(x: Int): Int = 0

@SpecOf("twiceModel")
fun twiceSpecification(x: Int) {
    preconditions { x >= 0 && x <= 1000 }
    postconditions<Int> { result -> result == 2 * x }
}
