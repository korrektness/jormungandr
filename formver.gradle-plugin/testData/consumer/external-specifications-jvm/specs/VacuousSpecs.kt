package stand.specs.vacuous

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("stand.lib.thrice")
@Pure fun thriceModel(x: Int): Int = 0

@SpecOf("thriceModel")
fun thriceSpec(x: Int) {
    postconditions<Int> { result -> result > x; result < x }
}
