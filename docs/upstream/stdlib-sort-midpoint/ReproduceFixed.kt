import copied.quickSort

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val size = 2147483639
    quickSort(UByteArray(size), 0, size - 1)
    println("sorted $size elements")
}
