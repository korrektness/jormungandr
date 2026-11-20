// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*
import kotlin.contracts.*

<!VIPER_TEXT!>class Box<!>(var n: Int)

fun <!VIPER_TEXT!>later<!>(f: () -> Unit) {}

fun <!VIPER_TEXT!>makeAny<!>(): Any? = { 1 }

fun <!VIPER_TEXT!>lambdaIsNonNull<!>() {
    val f: Any? = { 1 }
    verify(f != null)
}

fun <!VIPER_TEXT!>lambdasAreUnknown<!>() {
    val f: Any? = { 1 }
    val g: Any? = { 1 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>f === g<!>)
}

fun <!VIPER_TEXT!>returnedLambda<!>(): () -> Int = { 1 }

fun <!VIPER_TEXT!>inPlaceResultKnown<!>() {
    verify(run { 1 } == 1)
}

fun <!VIPER_TEXT!>invokedValueIsOpaque<!>() {
    val f = { 1 }
    val r = f()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 1<!>)
}

fun <!VIPER_TEXT!>trackedBeforeCreation<!>() {
    var x = 0
    x = 1
    val r = x
    verify(r == 1)
    later { x = 2 }
}

fun <!VIPER_TEXT!>sharedAfterCreation<!>() {
    var x = 0
    later { x = 2 }
    val r = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
}

fun <!VIPER_TEXT!>writeAfterCreationIsNotKept<!>() {
    var x = 0
    later { x = 2 }
    x = 1
    val r = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 1<!>)
}

fun <!VIPER_TEXT!>readOnlyCaptureIsShared<!>() {
    var x = 0
    later { <!INTEGER_OVERFLOW!>x + 1<!> }
    val r = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
}

fun <!VIPER_TEXT!>uncapturedVarStaysTracked<!>() {
    var x = 0
    var y = 0
    later { x = 2 }
    verify(y == 0)
}

fun <!VIPER_TEXT!>capturedValStaysKnown<!>() {
    val k = 3
    later { <!INTEGER_OVERFLOW!>k + 1<!> }
    verify(k == 3)
}

fun <!VIPER_TEXT!>capturedObjectIsShared<!>() {
    val b = Box(1)
    later { <!UNTRACKED_WRITE!>b.n = 2<!> }
    val r = b.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 1<!>)
}

fun <!VIPER_TEXT!>makeBox<!>(): @Unique Box = Box(1)

fun <!VIPER_TEXT!>inferredUniqueTrackedBeforeCapture<!>() {
    val b = makeBox()
    b.n = 2
    val r = b.n
    verify(r == 2)
    later { b.n = 3 }
}

fun <!VIPER_TEXT!>inferredUniqueSharedAfterCapture<!>() {
    val b = makeBox()
    b.n = 2
    later { b.n = 3 }
    val r = b.n
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 2<!>)
}

fun <!VIPER_TEXT!>consume<!>(b: @Unique Box) {}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>inferredUniqueNotConsumableAfterCapture<!>() {
    val b = makeBox()
    later { b.n = 3 }
    consume(<!UNIQUENESS_MISMATCH!>b<!>)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>inferredUniqueNotConsumableAfterStoredCapture<!>() {
    val b = makeBox()
    val touch = { b.n = 3 }
    consume(<!UNIQUENESS_MISMATCH!>b<!>)
}

fun <!VIPER_TEXT!>capturedSharedParameter<!>(b: Box) {
    later { b.n = 2 }
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>capturedUniqueParameter<!>(b: @Unique Box) {
    later <!INVALID_UNIQUENESS_CAPTURE!>{ b.n = 2 }<!>
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>callOnce<!>(f: () -> Unit) {
    contract { callsInPlace(f, InvocationKind.EXACTLY_ONCE) }
    f()
}

fun <!VIPER_TEXT!>calledInPlaceWithoutCapture<!>() {
    callOnce { }
}

fun <!VERIFICATION_SKIPPED!>calledInPlaceCapturingVar<!>() {
    var x = 0
    callOnce <!UNSUPPORTED_FEATURE!>{ x = 1 }<!>
}

fun <!VIPER_TEXT!>later2<!>(f: (Int) -> Int) {}

fun <!VIPER_TEXT!>bodyVerifies<!>() {
    later { verify(1 == 1) }
}

fun <!VIPER_TEXT!>bodyFails<!>() {
    later { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 2<!>) }
}

fun <!VIPER_TEXT!>bodyUsesParameter<!>() {
    later2 { n -> verify(n == n); n }
}

fun <!VIPER_TEXT!>bodyParameterUnknown<!>() {
    later2 { n -> verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n == 0<!>); n }
}

fun <!VIPER_TEXT!>bodyCapturedValIsUnknown<!>() {
    val k = 3
    later { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>k == 3<!>) }
}

fun <!VIPER_TEXT!>bodyCapturedValIsStable<!>(k: Int) {
    later {
        val a = k
        val b = k
        verify(a == b)
    }
}

fun <!VIPER_TEXT!>bodyCapturedVarIsUnknown<!>() {
    var x = 0
    later {
        val r = x
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
    }
}

fun <!VIPER_TEXT!>bodyCapturedVarReadsDiffer<!>() {
    var x = 0
    later {
        val a = x
        val b = x
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == b<!>)
    }
}

fun <!VIPER_TEXT!>bodyCapturedVarWriteIsLost<!>() {
    var x = 0
    later {
        x = 1
        val r = x
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 1<!>)
    }
}

fun <!VIPER_TEXT!>bodyLocalsAreTracked<!>() {
    later {
        var y = 0
        y = 2
        verify(y == 2)
    }
}

fun <!VIPER_TEXT!>bodyCapturedObjectIsShared<!>(b: Box) {
    later {
        b.n = 2
        val r = b.n
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 2<!>)
    }
}

fun <!VIPER_TEXT!>bodyNestedLambdaFails<!>() {
    later {
        later { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 2<!>) }
    }
}

inline fun <!VIPER_TEXT!>deferred<!>(crossinline f: () -> Unit) {
    later { f() }
}

fun <!VIPER_TEXT!>capturedLambdaTrackedBeforeCreation<!>() {
    var x = 0
    x = 1
    val r = x
    verify(r == 1)
    deferred { x = 2 }
}

fun <!VIPER_TEXT!>capturedLambdaSharesItsCaptures<!>() {
    var x = 0
    deferred { x = 1 }
    val r = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
}

fun <!VIPER_TEXT!>capturedLambdaBodyFails<!>() {
    deferred { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>1 == 2<!>) }
}

fun <!VIPER_TEXT!>bodyInPlaceLambdaReadsCapturedVal<!>() {
    val k = 3
    later { verify(run { k } == k) }
}

fun <!VIPER_TEXT!>bodyInPlaceLambdaCapturedValIsUnknown<!>() {
    val k = 3
    later { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>run { k } == 3<!>) }
}

fun <!VIPER_TEXT!>bodyInvokesCapturedVar<!>() {
    var f = {}
    later { f() }
}

fun <!VIPER_TEXT!>bodyNestedLambdaCapturesVar<!>() {
    var x = 0
    later { later { x = 1 } }
    val r = x
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
}

fun <!VIPER_TEXT!>forBodyVarTrackedBeforeCreation<!>() {
    for (i in 0..1) {
        var x = 0
        x = 1
        val r = x
        verify(r == 1)
        later { x = 2 }
    }
}

fun <!VIPER_TEXT!>forBodyVarSharedAfterCreation<!>() {
    for (i in 0..1) {
        var x = 0
        later { x = 2 }
        val r = x
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>r == 0<!>)
    }
}

<!VIPER_TEXT!>class Holder<!>(var box: @Unique Box, var m: Int)

fun <!VIPER_TEXT!>makeHolder<!>(): @Unique Holder = Holder(Box(1), 0)

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>capturedRootChildNotConsumable<!>() {
    val h = makeHolder()
    later { h.m = 1 }
    consume(<!UNIQUENESS_MISMATCH!>h.box<!>)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>movedChildStaysMovedAfterCapture<!>() {
    val h = makeHolder()
    consume(h.box)
    later { h.m = 1 }
    consume(<!INVALID_MOVED_ACCESS, UNIQUENESS_MISMATCH!>h.box<!>)
}
