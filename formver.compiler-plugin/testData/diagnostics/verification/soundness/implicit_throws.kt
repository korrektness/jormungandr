// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Each `...Catch` function returns 1 at runtime when its `try` throws part-way, so its postcondition must fail.

<!VERIFICATION_SKIPPED!>class Late<!> { lateinit var s: String }

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>lateinitCatch<!>(l: Late): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; l.s; x = 2 } catch (e: Exception) { }
    return x
}<!>

fun <!VIPER_TEXT!>lateinitCatchSeen<!>(l: Late): Int {
    postconditions<Int> { it == 1 || it == 2 }
    var x = 0
    try { x = 1; val n = l.s.length; x = 2 } catch (e: Exception) { }
    return x
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>localLateinitCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    lateinit var s: String
    var x = 0
    try { x = 1; val n = s.length; x = 2 } catch (e: Exception) { }
    return x
}<!>

fun <!VIPER_TEXT!>localLateinitCatchSeen<!>(): Int {
    postconditions<Int> { it == 1 || it == 2 }
    lateinit var s: String
    var x = 0
    try { x = 1; val n = s.length; x = 2 } catch (e: Exception) { }
    return x
}

// `System.getProperty` returns a platform-typed `String!`, which is null for an absent property.
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>platformAssignCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    val p = System.getProperty("snakt.absent")
    try { x = 1; val q: String = p; x = 2 } catch (e: Exception) { }
    return x
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>platformReassignCatch<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    var q: String = ""
    val p = System.getProperty("snakt.absent")
    try { x = 1; q = p; x = 2 } catch (e: Exception) { }
    return x
}<!>

fun <!VIPER_TEXT!>platformCheckedAssign<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    val p = System.getProperty("snakt.absent")
    if (p != null) {
        try { x = 1; val q: String = p; x = 2 } catch (e: Exception) { }
    }
    return x
}

// A declaration without a type keeps the platform type and checks nothing.
fun <!VIPER_TEXT!>platformKeptAssign<!>(): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    val p = System.getProperty("snakt.absent")
    try { x = 1; val q = p; x = 2 } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>platformAssignSeen<!>(): Int {
    postconditions<Int> { it == 1 || it == 2 }
    var x = 0
    val p = System.getProperty("snakt.absent")
    try { x = 1; val q: String = p; verify(q.length >= 0); x = 2 } catch (e: Exception) { }
    return x
}

// A message that is not a lambda takes the call path of `require` and `check`.
<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>requireMessageCatch<!>(message: () -> Any): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; require(false, message); x = 2 } catch (e: Exception) { }
    return x
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>checkMessageCatch<!>(message: () -> Any): Int {
    postconditions<Int> { it == 0 || it == 2 }
    var x = 0
    try { x = 1; check(false, message); x = 2 } catch (e: Exception) { }
    return x
}<!>

fun <!VIPER_TEXT!>requireMessagePasses<!>(a: Int, message: () -> Any): Int {
    postconditions<Int> { it == 1 || it == 2 }
    var x = 0
    try { x = 1; require(a > 0, message); verify(a > 0); x = 2 } catch (e: Exception) { }
    return x
}

fun <!VIPER_TEXT!>checkMessagePasses<!>(a: Int, message: () -> Any): Int {
    postconditions<Int> { it == 1 || it == 2 }
    var x = 0
    try { x = 1; check(a > 0, message); verify(a > 0); x = 2 } catch (e: Exception) { }
    return x
}
