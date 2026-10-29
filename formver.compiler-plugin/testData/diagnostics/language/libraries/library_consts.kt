// FULL_JDK
// WITH_STDLIB
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/externalConstants.kt

import external.constants.*
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>stdlibConsts<!>() {
    verify(
        Int.MAX_VALUE == 2147483647,
        Long.MAX_VALUE == 9223372036854775807L,
        Short.MIN_VALUE == (-32768).toShort(),
        Byte.MAX_VALUE == 127.toByte(),
        Char.MAX_VALUE.code == 65535,
    )
}

fun <!VIPER_TEXT!>libraryConsts<!>() {
    verify(
        INT_CONST == -7,
        LONG_CONST == 5000000000L,
        SHORT_CONST == 300.toShort(),
        BYTE_CONST == (-5).toByte(),
        CHAR_CONST == 'q',
        BOOLEAN_CONST,
        STRING_CONST.length == 2,
        Holder.NESTED == 11,
    )
}

fun <!VIPER_TEXT!>wrongLibraryConst<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Int.MAX_VALUE == 0<!>)
}
