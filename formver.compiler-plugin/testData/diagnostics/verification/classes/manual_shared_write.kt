// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
class Counter(var n: Int)

// The receiver is shared, so the write is dropped: it needs no permission.
fun <!VIPER_TEXT!>writeShared<!>(c: Counter) {
    c.n = 1
}

// An owned receiver keeps the write, which needs the unfolded predicate.
fun <!VIPER_TEXT!>writeOwned<!>(c: @Unique @Borrowed Counter) {
    unfold(UniquePred(c))
    c.n = 1
    fold(UniquePred(c))
}
