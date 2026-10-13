// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>castCrash<!>(x: Any): Int {
    val s = x as String
    return 1
}
fun <!VIPER_TEXT!>requireCrash<!>() { require(false) }
fun <!VIPER_TEXT!>checkCrash<!>() { check(false) }
fun <!VIPER_TEXT!>errorCrash<!>(): Nothing = error("boom")
fun <!VIPER_TEXT!>mayThrow<!>(x: Int): Int {
    if (x == 0) throw IllegalArgumentException()
    return 1
}
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>midTry<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try {
        x = 1
        mayThrow(0)
        x = 2
    } catch (e: Exception) { }
    return x
}<!>
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>nothingCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try {
        x = 1
        error("boom")
        x = 2
    } catch (e: Exception) { }
    return x
}<!>
fun <!VIPER_TEXT!>badSharedIndex<!>(a: IntArray): Int = <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[-1]<!>
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>requireCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; require(false); x = 2 } catch (e: Exception) { }
    return x
}<!>
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>checkCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; check(false); x = 2 } catch (e: Exception) { }
    return x
}<!>
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>castCatch<!>(a: Any): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val s = a as String; x = 2 } catch (e: Exception) { }
    return x
}<!>
fun <!VIPER_TEXT!>sharedSet<!>(a: IntArray) { <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[-1] = 7<!> }
fun <!VIPER_TEXT!>sharedGetCatch<!>(a: IntArray): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[-1]<!>; x = 2 } catch (e: Exception) { }
    return x
}
fun <!VIPER_TEXT!>longZeroCatch<!>(a: Long, b: Long): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO, INTEGER_OVERFLOW!>a / b<!>; x = 2 } catch (e: Exception) { }
    return x
}
fun <!VIPER_TEXT!>longRemZeroCatch<!>(a: Long, b: Long): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO!>a % b<!>; x = 2 } catch (e: Exception) { }
    return x
}
fun <!VIPER_TEXT!>shortZeroCatch<!>(a: Short, b: Short): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO!>a / b<!>; x = 2 } catch (e: Exception) { }
    return x
}
fun <!VIPER_TEXT!>byteZeroCatch<!>(a: Byte, b: Byte): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO!>a % b<!>; x = 2 } catch (e: Exception) { }
    return x
}
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>externalCatch<!>(s: String): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; s.substring(99); x = 2 } catch (e: Exception) { }
    return x
}<!>
fun <!VIPER_TEXT!>abort<!>(): Nothing { throw IllegalStateException() }
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>abortCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; abort(); x = 2 } catch (e: Exception) { }
    return x
}<!>
fun <!VIPER_TEXT!>shortZeroRemCatch<!>(a: Short, b: Short): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO!>a % b<!>; x = 2 } catch (e: Exception) { }
    return x
}
fun <!VIPER_TEXT!>byteZeroDivCatch<!>(a: Byte, b: Byte): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; val n = <!DIVISION_BY_ZERO!>a / b<!>; x = 2 } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>requireNormal<!>(x: Int): Int {
    postconditions<Int> { it > 0 }
    require(x > 0)
    return x
}

fun <!VIPER_TEXT!>checkNormal<!>(x: Int): Int {
    postconditions<Int> { it > 0 }
    check(x > 0)
    return x
}

fun <!VIPER_TEXT!>bangNormal<!>(x: Int?): Int {
    postconditions<Int> { it == x }
    return x!!
}

fun <!VIPER_TEXT!>bangCatch<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 0
    try {
        x = 1
        val absent: String? = null
        absent!!
        x = 2
    } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>castFailureOnly<!>(a: Any): Int {
    postconditions<Int> { it == 1 || it == 2 }
    var x = 0
    try { x = 1; val s = a as String; x = 2 } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>castNull<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 0
    try { x = 1; val a: Any? = null; a as String; x = 2 } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>castNullable<!>(a: Any?): String? = a as String?

fun <!VIPER_TEXT!>discardedSharedIndex<!>(a: IntArray) { <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[-1]<!> }

fun <!VIPER_TEXT!>sharedUpperIndex<!>(a: IntArray): Int = <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[a.size]<!>

fun <!VIPER_TEXT!>sharedUpperSet<!>(a: IntArray) { <!POSSIBLE_INDEX_OUT_OF_BOUND!>a[a.size] = 7<!> }

fun <!VIPER_TEXT!>sharedSetRetainsIndex<!>(a: IntArray) {
    preconditions { a.size > 0 }
    var i = -1
    <!POSSIBLE_INDEX_OUT_OF_BOUND!>a.set(i, run { i = 0; 7 })<!>
}

fun <!VIPER_TEXT!>sharedGetRetainsReceiver<!>(empty: IntArray, other: IntArray): Int {
    preconditions { empty.size == 0; other.size > 0 }
    var a = empty
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>a.get(run { a = other; 0 })<!>
}

fun <!VIPER_TEXT!>validSharedAccess<!>(a: IntArray, i: Int) {
    preconditions { i >= 0; i < a.size }
    a[i] = 7
    a[i]
}

fun <!VIPER_TEXT!>zeroDivisionStaysError<!>(x: Int): Int {
    try { return <!DIVISION_BY_ZERO, DIVISION_BY_ZERO!>x / 0<!> } catch (e: Exception) { return 0 }
}

fun <!VIPER_TEXT!>overflowStaysError<!>(x: Int): Int {
    preconditions { x == 2147483647 }
    try { return <!INTEGER_OVERFLOW!>x + 1<!> } catch (e: Exception) { return 0 }
}

<!VIPER_VERIFICATION_ERROR!>@Pure fun <!VIPER_TEXT!>discardedPureIndex<!>(a: @Unique IntArray): Int {
    a[-1]
    return 0
}<!>

<!PURITY_VIOLATION!>@Pure fun <!VERIFICATION_SKIPPED!>specificationCast<!>(a: Any): String = a as String<!>

<!VIPER_TEXT!>class ThrowingAccess<!> {
    var value: Int
        <!VIPER_TEXT!>get<!>() = error("getter")
        <!VIPER_TEXT!>set<!>(v) { error("setter") }
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>getterCatch<!>(a: ThrowingAccess): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; a.value; x = 2 } catch (e: Exception) { }
    return x
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>setterCatch<!>(a: ThrowingAccess): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; a.value = 7; x = 2 } catch (e: Exception) { }
    return x
}<!>

fun <!VIPER_TEXT!>requireMessageCatch<!>(): Int {
    postconditions<Int> { it == 3 }
    var x = 0
    try {
        x = 1
        require(false) { x = 3; "message" }
        x = 2
    } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>checkMessageNotEvaluated<!>(): Int {
    postconditions<Int> { it == 1 }
    var x = 0
    try {
        x = 1
        check(true) { x = 3; "message" }
    } catch (e: Exception) { }
    return x
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>functionObjectCatch<!>(action: () -> Unit): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; action(); x = 2 } catch (e: Exception) { }
    return x
}<!>
