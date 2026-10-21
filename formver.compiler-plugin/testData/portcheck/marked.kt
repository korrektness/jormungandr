<!VIPER_TEXT!>class Fixture {
    private lateinit var elementData: Array<Any?>
    fun <!VERIFICATION_SKIPPED!>reset<!>() {
        <!UNSUPPORTED_OWNERSHIP!>elementData = arrayOfNulls(1)<!>
    }
    fun answer(): Int {
        val marker = 0
        <!ASSERTION_ERROR!>return 1<!>
    }
    fun loop() {
        var i = 0
        while (i < 2) {
            i++
        }
    }
}<!>
