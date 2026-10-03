// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.Borrowed

val sharedValue: Any = Any()

fun share(x: Any) {}
fun consume(x: @Unique Any) {}

class Node {
    var child: @Unique Any = Any()
    var count: Int = 0
}

fun `assign unique default argument from constructor call`(
    x: @Unique Any = Any()
) {}

fun `assign shared default argument for unique parameter`(
    x: @Unique Any = <!UNIQUENESS_MISMATCH!>sharedValue<!>
) {}

fun `assign shared parameter as unique default argument`(
    x: Any,
    y: @Unique Any = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT, UNIQUENESS_MISMATCH!>x<!>
) {
    val z = x
}

class `assign unique default argument in constructor`(
    val x: @Unique Any = Any(),
)

class `assign shared default argument in constructor`(
    val x: @Unique Any = <!UNIQUENESS_MISMATCH!>sharedValue<!>,
)

fun `assign unique argument as unique default argument`(
    x: @Unique Any,
    y: @Unique Any = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>x<!>
) {
    val z = <!INVALID_MOVED_ACCESS!>x<!>
}

fun `chain assign unique argument as unique default argument`(
    x: @Unique Any,
    y: @Unique Any = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT, INVALID_DUPLICATE_UNIQUE_ARGUMENT!>x<!>,
    z: @Unique Any = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT, INVALID_DUPLICATE_UNIQUE_ARGUMENT, INVALID_MOVED_ACCESS!>x<!>
) {
    val z = x
}

fun `share default argument resolved from shared parameter twice`(
    x: Any,
    y: Any = x,
) {
    share(y)
    share(y)
}

fun `share constructor default for shared parameter twice`(
    y: Any = Any(),
) {
    share(y)
    share(y)
}

fun `share shared parameter with constructor default after assignment`(
    y: Any = Any(),
) {
    val z = y
    share(y)
}

fun `shared parameter with constructor default cannot initialize unique local`(
    y: Any = Any(),
) {
    val z: @Unique Any = <!UNIQUENESS_MISMATCH!>y<!>
}

fun `reuse shared constructor default across multiple defaults`(
    y: Any = Any(),
    z: Any = y,
    w: Any = y,
) {}

fun `escape inconsistent parameter in function default argument`(
    b: @Unique Node,
    moved: @Unique Any = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>b.child<!>,
    escaped: Unit = consume(<!ESCAPE_UNIQUENESS_INCONSISTENCY!>b<!>),
) {}

class EscapeFromConstructorDefaultArgument(
    b: @Unique Node,
    moved: @Unique Any = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>b.child<!>,
    escaped: Unit = consume(<!ESCAPE_UNIQUENESS_INCONSISTENCY!>b<!>),
)

fun `assign shared default argument for borrowed parameter`(
    x: @Borrowed Any = sharedValue,
) {}

fun `share unique parameter as default argument`(
    x: @Unique Any,
    y: Any = <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>x<!>,
) {}

fun `share unique field of unique parameter as default argument`(
    b: @Unique Node,
    c: Any = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>b.child<!>,
) {}

fun `read value field of unique parameter as default argument`(
    b: @Unique Node,
    n: Int = b.count,
) {}

fun `share shared parameter as default argument`(
    x: Any,
    y: Any = x,
) {}

fun @Unique Node.`share unique field of unique receiver as default argument`(
    c: Any = <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>child<!>,
) {}
