// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

abstract class Super(
    var x: @Unique Int
)

@Manual
class Test(
    x: Int
) : Super(<!UNIQUENESS_MISMATCH!>x<!>)

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>test<!>(p: @Unique Test) {
    unfold(UniquePred(p))
    unfold(UniquePred(<!INVALID_MOVED_ACCESS!>p<!> as Super))
    <!INVALID_MOVED_ACCESS!>p<!>.x = <!UNIQUENESS_MISMATCH!>5<!>
    fold(UniquePred(<!INVALID_MOVED_ACCESS!>p<!> as Super))
    fold(UniquePred(<!INVALID_MOVED_ACCESS!>p<!>))
}


@Manual
class Tree(
    var left: @Unique Tree?,
    var right: @Unique Tree?,
    var data: @Unique Int,
)


fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>contains<!>(tree: @Unique @Borrowed Tree?, search: Int) : Boolean {
    if (tree == null) return false
    unfold(UniquePred(<!LOCALITY_MISMATCH!>tree<!>))
    if (<!INVALID_MOVED_ACCESS!>tree<!>.data == search) {
        fold(UniquePred(<!INVALID_MOVED_ACCESS, LOCALITY_MISMATCH!>tree<!>))
        <!EXIT_UNIQUENESS_INCONSISTENCY!>return true<!>
    }
    val res = contains(<!INVALID_MOVED_ACCESS!>tree<!>.left, search) || contains(<!INVALID_MOVED_ACCESS!>tree<!>.right, search)
    fold(UniquePred(<!INVALID_MOVED_ACCESS, LOCALITY_MISMATCH!>tree<!>))
    <!EXIT_UNIQUENESS_INCONSISTENCY!>return res<!>
}


fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>combine<!>(left: @Unique Tree, right: @Unique Tree): @Unique Tree {
    unfold(UniquePred(left))
    unfold(UniquePred(right))
    val data = <!INVALID_MOVED_ACCESS!>left<!>.data + <!INVALID_MOVED_ACCESS!>right<!>.data
    fold(UniquePred(<!ESCAPE_UNIQUENESS_INCONSISTENCY, INVALID_MOVED_ACCESS!>left<!>))
    fold(UniquePred(<!ESCAPE_UNIQUENESS_INCONSISTENCY, INVALID_MOVED_ACCESS!>right<!>))
    val res = Tree(<!ESCAPE_UNIQUENESS_INCONSISTENCY, INVALID_MOVED_ACCESS!>left<!>, <!ESCAPE_UNIQUENESS_INCONSISTENCY, INVALID_MOVED_ACCESS!>right<!>, <!UNIQUENESS_MISMATCH!>data<!>)
    return <!UNIQUENESS_MISMATCH!>res<!>
}
