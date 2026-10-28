// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Elements {
    @Pure @Unique fun <!VIPER_TEXT!>elementAt<!>(i: Int): Any?
}

<!VIPER_TEXT!>class Payload<!>

<!VIPER_TEXT!>class ElementBuffer<!>(val elementData: @Unique Array<Any?>, val payload: @Unique Payload) : Elements {
    @Pure @Unique override fun <!VIPER_TEXT!>elementAt<!>(i: Int): Any? {
        preconditions { i >= 0 && i < elementData.size }
        return elementData[i]
    }

    @Suppress("UNCHECKED_CAST")
    @Pure @Unique fun <E> <!VIPER_TEXT!>castElementAt<!>(i: Int): E {
        preconditions { i >= 0 && i < elementData.size }
        return elementData[i] as E
    }

    @Pure @Unique fun <!VERIFICATION_SKIPPED!>ownedObject<!>(): Payload = <!EXIT_UNIQUENESS_INCONSISTENCY, PURITY_VIOLATION!>payload<!>

    <!VIPER_VERIFICATION_ERROR!>@Pure @Unique fun <!VIPER_TEXT!>pastEnd<!>(): Any? = elementData[elementData.size]<!>

    <!VIPER_VERIFICATION_ERROR!>@Pure @Unique fun <!VIPER_TEXT!>checkedElement<!>(): String = <!CAST_NOT_ESTABLISHED!>elementData[0] as String<!><!>
}

@Pure fun <!VIPER_TEXT!>fromParameter<!>(a: @Unique Array<Any?>, i: Int): Any? {
    preconditions { i >= 0 && i < a.size }
    return a[i]
}

<!PURITY_VIOLATION!>@Pure
fun <!VERIFICATION_SKIPPED!>guardedCheckedElement<!>(a: @Unique Array<Any?>): <!INVALID_PURE_REFERENCE_RESULT!>Payload<!> {
    preconditions { a.size > 0 && a[0] is Payload }
    return a[0] as Payload
}<!>

interface SizedBuffer {
    @Pure val size: Int
}

<!VIPER_TEXT!>class MutableSize<!> : SizedBuffer {
    <!INVALID_OBSERVER!>@Pure override var size: Int = 0<!>
    fun <!VIPER_TEXT!>first<!>(): Int = 1
    fun <!VIPER_TEXT!>second<!>(): Int = 2
    @Pure fun <!VIPER_TEXT!>third<!>(): Int = 3
}
