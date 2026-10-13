// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Box<!>(var value: Int)

fun @Unique @Borrowed Box.<!VIPER_TEXT!>countUp<!>(n: Int) {
    preconditions { 0 <= n }
    refute(false)
    var i = 0
    value = 0
    while (i < n) {
        loopInvariants { 0 <= i && i <= n && value == i }
        value = value + 1
        i++
        refute(false)
    }
    refute(false)
    verify(value == n)
}

fun @Unique @Borrowed Box.<!VIPER_TEXT!>countUpEndProbe<!>(n: Int) {
    preconditions { 0 <= n && n < 2147483647 }
    var i = 0
    value = 0
    while (i < n) {
        loopInvariants { 0 <= i && i <= n && value == i }
        value = value + 1
        i++
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>value == n + 1<!>)
}

// The loop leaves the receiver alone; it is owned at the head and handed back at exit.
fun @Unique @Borrowed Box.<!VIPER_TEXT!>countIntoParameter<!>(b: @Unique @Borrowed Box, n: Int) {
    preconditions { 0 <= n }
    refute(false)
    var i = 0
    b.value = 0
    value = 7
    while (i < n) {
        loopInvariants { 0 <= i && i <= n && b.value == i }
        b.value = b.value + 1
        i++
        refute(false)
    }
    refute(false)
    verify(b.value == n)
}
