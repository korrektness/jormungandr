// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class ReferenceStorage<!>(val storage: @Unique Array<Any?>, val first: Any?, val second: Any?) {
    @Pure fun <!VIPER_TEXT!>elementAt<!>(i: Int): Any? = if (i == 0) first else second

    @Unique @Borrowed fun <!VIPER_TEXT!>get<!>(i: Int): Any? {
        preconditions { storage.size == 2; 0 <= i && i < 2 }
        postconditions<Any?> { it === elementAt(i) }
        refute(false)
        storage[0] = first
        storage[1] = second
        return storage[i]
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>wrongSlot<!>(): Any? {
        preconditions { storage.size == 2; first !== second }
        postconditions<Any?> { it === elementAt(0) }
        refute(false)
        storage[0] = first
        storage[1] = second
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return storage[1]<!>
    }

    @Unique @Borrowed fun <!VIPER_TEXT!>preserve<!>(i: Int) {
        preconditions { storage.size == 2; 0 <= i && i < 2 }
        refute(false)
        var j = 0
        while (j < 2) {
            loopInvariants { 0 <= j && j <= 2; first === elementAt(0) }
            val value = get(i)
            verify(value === elementAt(i))
            refute(false)
            j++
        }
        refute(false)
    }
}

fun <!VERIFICATION_SKIPPED!>executed<!>(a: Any?, b: Any?): Boolean = <!UNSUPPORTED_FEATURE!>a === b<!>

fun <!VERIFICATION_SKIPPED!>unused<!>(a: Any?, b: Any?) { <!UNSUPPORTED_FEATURE!>a === b<!> }

@Pure fun <!VERIFICATION_SKIPPED!>pureBody<!>(a: Any?, b: Any?): Boolean = <!UNSUPPORTED_FEATURE!>a === b<!>

interface ReferenceLaw {
    @Pure fun <!VIPER_TEXT!>element<!>(): Any?
    @Law @Pure fun reflexive(): Boolean = element() === element()
}

fun <!VIPER_TEXT!>injectedValue<!>() {
    val a: Any? = 1000
    val b: Any? = 1000
    verify(a === b)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a !== b<!>)
}
