@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val size = 2147483639
    UByteArray(size).sort()
    println("sorted $size elements")
}
