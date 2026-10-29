package stand.specs.verified

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("stand.vlib.half")
@Pure fun halfModel(x: Int): Int = 0

@SpecOf("halfModel")
fun halfSpec(x: Int) {
    preconditions { x >= 0 }
    postconditions<Int> { result -> result <= x }
}

@ExternalSpec("stand.vlib.Box")
interface BoxModel {
    @Pure @get:Unique val size: Int

    @Law @Pure @Unique fun single(): Boolean = size == 1
}
