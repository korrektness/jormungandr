import org.jetbrains.kotlin.formver.plugin.Unique

<!VIPER_TEXT!>class Counter<!>(var n: Int)

fun <!VIPER_TEXT!>make<!>(): @Unique Counter = Counter(0)

fun <!VIPER_TEXT!>keep<!>(c: Counter) {}

val root = make()

fun <!VIPER_TEXT!>`read twice`<!>(): Int {
    keep(<!OPAQUE_CALL!>root<!>)
    keep(<!OPAQUE_CALL!>root<!>)
    return <!OPAQUE_CALL!>root<!>.n
}

<!VIPER_TEXT!>object Paths<!> {
    val root = make()
}

fun <!VIPER_TEXT!>`read an object property twice`<!>(): Int {
    keep(Paths.root)
    keep(Paths.root)
    return Paths.root.n
}
