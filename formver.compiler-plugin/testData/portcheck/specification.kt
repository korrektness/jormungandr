class Fixture {
    private lateinit var elementData: Array<Any?>
    @Law
    fun valid() = elementData.size >= 0
    @SpecOf("reset")
    fun resetSpec() {
        postconditions { elementData.size == 1 }
    }
    @Unique fun reset() {
        preconditions { true }
        postconditions { elementData.size == 1 }
        elementData = arrayOfNulls(1)
        verify(elementData.size == 1)
    }
    @Pure @Borrowed @WrappingIntArithmetic @TrustedElementEquality fun answer(): Int {
        preconditions {
            "}".length == 1 // braces in strings and comments: }
        }
        postconditions { result == 1 }
        val marker = 0
        verify(marker == 0)
        return 1
    }
    fun loop() {
        var i = 0
        while (i < 2) {
            loopInvariants { i >= 0 }
            i++
        }
    }
}
