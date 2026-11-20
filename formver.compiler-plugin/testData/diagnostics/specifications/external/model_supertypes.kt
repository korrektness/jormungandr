// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

<!VIPER_TEXT!>class ContentType<!>(val name: String)

<!VIPER_TEXT!>class Registry<!> {
    private val types: MutableList<ContentType> = mutableListOf()

    fun <!VIPER_TEXT!>accept<!>(vararg added: ContentType) {
        <!OPAQUE_CALL!>types += added<!>
    }

    fun <!VIPER_TEXT!>asCollection<!>(): MutableCollection<ContentType> = types
}

fun <!VIPER_TEXT!>localAsCollection<!>(): Collection<Int> {
    val list = mutableListOf<Int>()
    return list
}

fun <!VIPER_TEXT!>firstOfEmpty<!>(): Int {
    val list = mutableListOf<Int>()
    return <!VIPER_VERIFICATION_ERROR!>list[0]<!>
}
