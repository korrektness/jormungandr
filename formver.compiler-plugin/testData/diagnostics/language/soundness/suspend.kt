// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Counter<!>(var n: Int)

suspend fun <!VIPER_TEXT!>pause<!>() {}

suspend fun <!VIPER_TEXT!>localAcrossSuspension<!>(x: Int) {
    var k = x
    pause()
    verify(k == x)
}

suspend fun <!VIPER_TEXT!>ownedAcrossSuspension<!>() {
    val c: @Unique Counter = Counter(1)
    pause()
    verify(c.n == 1)
}

suspend fun <!VIPER_TEXT!>sharedAcrossSuspension<!>(c: Counter) {
    val before = c.n
    pause()
    val after = c.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>after == before<!>)
}
