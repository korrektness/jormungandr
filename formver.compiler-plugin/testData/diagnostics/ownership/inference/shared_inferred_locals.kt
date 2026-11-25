import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.verify

<!VIPER_TEXT!>class Counter<!>(var n: Int)

<!VIPER_TEXT!>class Holder<!>(var item: Counter)

fun <!VIPER_TEXT!>make<!>(): @Unique Counter = Counter(0)

fun <!VIPER_TEXT!>keep<!>(c: Counter) {}

fun Counter.<!VIPER_TEXT!>peek<!>(): Int = 0

fun <!VIPER_TEXT!>`read after passing to a shared parameter`<!>(): Int {
    val c = make()
    keep(c)
    return c.n
}

fun <!VIPER_TEXT!>`read after passing as a shared receiver`<!>(): Int {
    val c = make()
    c.peek()
    return c.n
}

fun <!VIPER_TEXT!>`facts before sharing hold`<!>() {
    val c = make()
    c.n = 1
    verify(c.n == 1)
    keep(c)
}

fun <!VIPER_TEXT!>`facts after sharing are lost`<!>() {
    val c = make()
    c.n = 1
    keep(c)
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`shared on one branch`<!>(b: Boolean) {
    val c = make()
    c.n = 1
    if (b) keep(c)
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`shared in a loop`<!>(k: Int) {
    val c = make()
    var i = 0
    while (i < k) {
        keep(c)
        i++
    }
}

fun <!VIPER_TEXT!>`assigned a shared value`<!>(other: Counter) {
    var c = make()
    c.n = 1
    c = other
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`alias of a shared local`<!>(): Int {
    val c = make()
    keep(c)
    val d = c
    return d.n
}

fun <!VIPER_TEXT!>`facts are lost after copying into an inferred local`<!>() {
    val c = make()
    c.n = 1
    val d = c
    keep(d)
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`facts are lost after assigning to an inferred local`<!>() {
    val c = make()
    c.n = 1
    var d = make()
    d = c
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`facts are lost after storing in a shared property`<!>(h: Holder) {
    val c = make()
    c.n = 1
    h.item = c
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`facts are lost after storing in a shared local`<!>() {
    val c = make()
    c.n = 1
    val d: Counter = c
    keep(d)
    val k = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 1<!>)
}

fun <!VIPER_TEXT!>`modelled member on a shared collection`<!>(): Int {
    val xs = mutableListOf<Int>()
    keepList(xs)
    <!UNCHECKED_LIBRARY_CALL!>xs.add(1)<!>
    return <!UNCHECKED_LIBRARY_CALL!>xs.size<!>
}

fun <!VIPER_TEXT!>keepList<!>(xs: MutableList<Int>) {}
