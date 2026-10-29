import org.jetbrains.kotlin.formver.plugin.*

public fun commonPositive(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}
