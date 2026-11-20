package external.models

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("external.library.checkedDigit")
fun checkedDigitModel(d: Int): Int = 0

@SpecOf("checkedDigitModel")
fun checkedDigitSpec(d: Int) {
    postconditions<Int> { result -> result == d }
    throwsOnlyIf { d < 0 || d > 9 }
}

@ExternalFunction("external.library.uncheckedDigit")
fun uncheckedDigitModel(d: Int): Int = 0

@SpecOf("uncheckedDigitModel")
fun uncheckedDigitSpec(d: Int) {
    postconditions<Int> { result -> result == d }
}
