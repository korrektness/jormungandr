// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

@AlwaysVerify
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

@AlwaysVerify
fun @Unique @Borrowed Box.<!VIPER_TEXT!>countUpEndProbe<!>(n: Int) {
    preconditions { 0 <= n }
    var i = 0
    value = 0
    while (i < n) {
        loopInvariants { 0 <= i && i <= n && value == i }
        value = value + 1
        i++
    }
    verify(<!VIPER_VERIFICATION_ERROR!>value == n + 1<!>)
}

// The loop leaves the receiver alone; it is owned at the head and handed back at exit.
@AlwaysVerify
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
