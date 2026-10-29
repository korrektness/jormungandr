import org.jetbrains.kotlin.formver.plugin.*

fun jvmTestPositive(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}

fun jvmTestFiltered(values: @Unique @Borrowed List<Int>) {
    val positive = values.filter { it > 0 }
    positive.forEach { verify(it > 0) }
}
