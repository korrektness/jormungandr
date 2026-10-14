// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class CtorAnnotated @NeverVerify constructor(first: String) {
    private val items: MutableList<String> = mutableListOf()
    init { items.add(first) }
}

@NeverVerify
class ClassAnnotated(first: String) {
    private val items: MutableList<String> = mutableListOf()
    init { items.add(first) }

    fun <!VIPER_TEXT!>member<!>(): Int {
        postconditions<Int> { it == 1 }
        return 0
    }

    class Nested {
        fun <!VIPER_TEXT!>nestedMember<!>(): Int {
            postconditions<Int> { it == 1 }
            return 0
        }
    }
}

@NeverConvert
class ClassNotConverted(first: String) {
    private val items: MutableList<String> = mutableListOf()
    init { items.add(first) }

    fun member(): Int {
        postconditions<Int> { it == 1 }
        return 0
    }
}

// The construction still runs through the unannotated constructor, which is verified.
<!VIPER_TEXT!>class SecondaryAnnotated<!>(val count: Int) {
    init { verify(count == count) }

    @NeverVerify
    constructor(first: String) : this(first.length)
}

class Stored @NeverVerify constructor(val value: Int)

// An unverified construction gives callers no field values.
fun <!VIPER_TEXT!>readStored<!>(): Int {
    postconditions<Int> { it == 1 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return Stored(1).value<!>
}

// Without an opt-out, the same construction fails.
<!VIPER_TEXT!>class Unannotated<!>(first: String) {
    init { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first.length == 0<!>) }
}
