// FULL_JDK

<!VIPER_TEXT!>class ShadowingWriter<!> : java.io.StringWriter() {
    private val <!PROPERTY_HIDES_JAVA_FIELD!>lock<!>: Int = 1
    val copy: Int = lock
}
