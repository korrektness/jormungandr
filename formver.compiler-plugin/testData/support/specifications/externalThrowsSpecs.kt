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

@ExternalFunction("kotlin.text.trim")
fun String.trimModel(): String = TODO()

@SpecOf("trimModel")
fun String.trimSpec() {
    preconditions { this == "allowed" }
}

@ExternalSpec("java.io.File")
interface FileModel

@ExternalConstructor("java.io.File")
fun fileModel(path: String): FileModel = TODO()

@SpecOf("fileModel")
fun fileSpec(path: String) {
    throwsOnlyIf { path == "denied" }
}

@ExternalFunction("kotlin.let")
fun <T, R> T.letModel(block: (T) -> R): R = TODO()

@SpecOf("letModel")
fun <T, R> T.letSpec(block: (T) -> R) {
    preconditions { "denied" != this }
}
