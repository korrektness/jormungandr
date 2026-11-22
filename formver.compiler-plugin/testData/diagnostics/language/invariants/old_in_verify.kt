// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Reader<!>(var pos: Int)

fun <!VIPER_TEXT!>bump<!>(r: @Unique @Borrowed Reader) {
    preconditions { r.pos == 5 }
    r.pos = r.pos + 1
    verify(old(r.pos) == 5, r.pos == 6)
}

fun @Unique @Borrowed Reader.<!VIPER_TEXT!>skip<!>(n: Int) {
    preconditions { pos == 0 && n >= 0 }
    var i = 0
    while (i < n) {
        loopInvariants { i <= n && pos == i }
        pos = pos + 1
        i = i + 1
        verify(old(pos) == 0, pos == i)
    }
}
