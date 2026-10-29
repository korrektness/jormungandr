package external.partial

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("external.library.twice")
@Pure fun twiceModel(x: Int): Int = 0

// The postcondition contradicts itself for `x == 3` only.
@SpecOf("twiceModel")
fun twiceSpecification(x: Int) {
    preconditions { x >= 0 && x <= 1000 }
    postconditions<Int> { result -> result == 2 * x; x != 3 || result != 6 }
}
