package stand.specs.bad

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("stand.lib.Missing")
interface MissingModel

@ExternalFunction("stand.lib.missing")
fun missingModel(x: Int): Int = 0
