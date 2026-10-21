class Fixture {
    private lateinit var elementData: Array<Any?>
    fun reset() {
        elementData = arrayOfNulls(1)
    }
    fun answer(): Int {
        val marker = 0
        return 1
    }
    fun loop() {
        var i = 0
        while (i < 2) {
            i++
        }
    }
}
