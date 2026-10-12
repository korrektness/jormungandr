// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

fun interface CounterSpec {
    fun invoke(): Int
    @GhostObserver @Pure fun value(): Int = ghostObserver()
    @GhostObserver @Pure fun step(): Int = ghostObserver()
}

fun makeCounter(start: Int, step: Int): @Unique CounterSpec {
    var n = start
    return CounterSpec {
        exposes(CounterSpec::class) { value() == n; step() == step }
        n += step
        n
    }
}

fun copiedValue(start: Int): @Unique CounterSpec {
    val increment = start
    val counter: @Unique CounterSpec = CounterSpec {
        exposes(CounterSpec::class) { value() == increment; step() == increment }
        increment
    }
    println(increment)
    return counter
}

class CaptureBox(var n: Int)

fun ownedCapture(box: @Unique CaptureBox): @Unique CounterSpec = CounterSpec {
    exposes(CounterSpec::class) { value() == box.n; step() == 1 }
    box.n++
}

fun mutableRead() {
    var n = 0
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun ownedRead(box: @Unique CaptureBox) {
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == box.n; step() == 1 }
        box.n++
    }
    println(<!INVALID_MOVED_ACCESS!>box<!>.n)
}

fun plainLambda(box: @Unique CaptureBox) {
    val counter = <!INVALID_UNIQUENESS_CAPTURE!>{ box.n++ }<!>
}

fun interface PlainCounter { fun invoke(): Int }

fun plainSam(box: @Unique CaptureBox) {
    val counter = PlainCounter <!INVALID_UNIQUENESS_CAPTURE!>{ box.n++ }<!>
}

fun mutableWrite() {
    var n = 0
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        ++n
    }
    <!INVALID_MOVED_ACCESS!>n<!> = 42
}

fun capturedTwice() {
    var n = 0
    val first = CounterSpec {
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        ++n
    }
    val second = CounterSpec <!INVALID_MOVED_ACCESS!>{
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        ++n
    }<!>
}

fun ownedLocal() {
    val box: @Unique CaptureBox = CaptureBox(0)
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == box.n; step() == 1 }
        box.n++
    }
    println(<!INVALID_MOVED_ACCESS!>box<!>.n)
}

fun specOnlyCapture() {
    var n = 0
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        1
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun acceptCounter(counter: @Unique CounterSpec) {}

fun implicitSam() {
    var n = 0
    acceptCounter {
        exposes(CounterSpec::class) { value() == n; step() == 1 }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun interface InheritedCounter : CounterSpec

fun inheritedCapture() {
    var n = 0
    val counter = InheritedCounter {
        exposes(InheritedCounter::class) { value() == n; step() == 1 }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

interface CounterBridge : CounterSpec
fun interface BridgedCounter : CounterBridge

fun bridgedPositive(start: Int): @Unique BridgedCounter {
    var n = start
    return BridgedCounter {
        exposes(BridgedCounter::class) { value() == n; step() == 1 }
        ++n
    }
}

fun bridgedNegative() {
    var n = 0
    val counter = BridgedCounter {
        exposes(BridgedCounter::class) { value() == n; step() == 1 }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun interface Observed {
    fun invoke(): Int
    @GhostObserver @Pure fun value(): Int = ghostObserver()
}

fun interface ObservedSpec : Observed

interface ObservedMiddle : Observed
fun interface DeepObservedSpec : ObservedMiddle

fun inheritedOrdinaryPositive(start: Int): @Unique ObservedSpec {
    var n = start
    return ObservedSpec {
        exposes(ObservedSpec::class) { value() == n }
        ++n
    }
}

fun inheritedOrdinaryNegative() {
    var n = 0
    val counter = ObservedSpec {
        exposes(ObservedSpec::class) { value() == n }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun inheritedDeepPositive(start: Int): @Unique DeepObservedSpec {
    var n = start
    return DeepObservedSpec {
        exposes(DeepObservedSpec::class) { value() == n }
        ++n
    }
}

fun inheritedDeepNegative() {
    var n = 0
    val counter = DeepObservedSpec {
        exposes(DeepObservedSpec::class) { value() == n }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
}

fun valueParameterCopy(increment: Int): @Unique CounterSpec {
    val counter: @Unique CounterSpec = CounterSpec {
        exposes(CounterSpec::class) { value() == increment; step() == increment }
        increment
    }
    println(increment)
    return counter
}

fun mutableReference() {
    var box: CaptureBox = CaptureBox(0)
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == box.n; step() == 1 }
        box.n++
    }
    println(<!INVALID_MOVED_ACCESS!>box<!>.n)
}

fun sharedVal(box: CaptureBox) {
    val counter = CounterSpec {
        exposes(CounterSpec::class) { value() == box.n; step() == 1 }
        box.n++
    }
    println(box.n)
}

class OwnedReceiver(var n: Int) {
    @Unique fun captureThis() {
        val counter = CounterSpec {
            exposes(CounterSpec::class) { value() == 0; step() == 1 }
            ++n
        }
        println(<!INVALID_MOVED_ACCESS!>n<!>)
    }
}

class RootWithChild(var child: @Unique CaptureBox)

fun consumeBox(box: @Unique CaptureBox) {}

fun partialCapture(root: @Unique RootWithChild) {
    consumeBox(root.child)
    val counter = CounterSpec <!INVALID_MOVED_ACCESS!>{
        exposes(CounterSpec::class) { value() == root.child.n; step() == 1 }
        root.child.n++
    }<!>
}

fun interface InvalidBodySpec {
    fun invoke(): Int
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun value(): Int = 0<!>
}

fun invalidBodyCapture(box: @Unique CaptureBox) {
    val counter = InvalidBodySpec <!INVALID_UNIQUENESS_CAPTURE!>{
        exposes(InvalidBodySpec::class) { value() == box.n }
        box.n++
    }<!>
    println(box.n)
}

interface InvalidObserverBase {
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun value(): Int = ghostObserver()<!>
}

fun interface InvalidInheritedSpec : InvalidObserverBase {
    fun invoke(): Int
}

fun invalidInheritedCapture(box: @Unique CaptureBox) {
    val counter = InvalidInheritedSpec <!INVALID_UNIQUENESS_CAPTURE!>{
        exposes(InvalidInheritedSpec::class) { value() == box.n }
        box.n++
    }<!>
    println(box.n)
}

fun interface MixedInvalidSpec : CounterSpec {
    <!INVALID_OBSERVER!>@GhostObserver fun bad(): Int = ghostObserver()<!>
}

fun mixedInvalidCapture(box: @Unique CaptureBox) {
    val counter = MixedInvalidSpec <!INVALID_UNIQUENESS_CAPTURE!>{
        exposes(MixedInvalidSpec::class) { value() == box.n; step() == 1; bad() == 0 }
        box.n++
    }<!>
    println(box.n)
}

<!FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS!>fun<!> interface NoSamSpec {
    @GhostObserver @Pure fun value(): Int = ghostObserver()
}

fun noSamCapture(box: @Unique CaptureBox) {
    val counter = <!INTERFACE_AS_FUNCTION!>NoSamSpec<!> <!INVALID_UNIQUENESS_CAPTURE!>{ box.n++ }<!>
    println(box.n)
}

fun interface InvalidOverrideSpec : CounterSpec {
    <!INVALID_OBSERVER!>@Pure override fun value(): Int = 0<!>
}

fun invalidOverrideCapture(box: @Unique CaptureBox) {
    val counter = InvalidOverrideSpec <!INVALID_UNIQUENESS_CAPTURE!>{
        exposes(InvalidOverrideSpec::class) { step() == 1 }
        box.n++
    }<!>
    println(box.n)
}

fun interface DeepInvalidOverrideSpec : InvalidOverrideSpec {
    @Pure override fun value(): Int = 1
}

fun deepInvalidOverrideCapture(box: @Unique CaptureBox) {
    val counter = DeepInvalidOverrideSpec <!INVALID_UNIQUENESS_CAPTURE!>{
        exposes(DeepInvalidOverrideSpec::class) { step() == 1 }
        box.n++
    }<!>
    println(box.n)
}

fun interface ParameterizedObserverSpec {
    fun invoke(): Int
    <!INVALID_OBSERVER!>@GhostObserver @Pure fun value(i: Int): Int = ghostObserver()<!>
}

fun parameterizedObserverCapture(box: @Unique CaptureBox) {
    val counter = ParameterizedObserverSpec <!INVALID_UNIQUENESS_CAPTURE!>{
        <!INVALID_EXPOSES!>exposes(ParameterizedObserverSpec::class) { value(0) == box.n }<!>
        box.n++
    }<!>
    println(box.n)
}

<!FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS!>fun<!> interface MultipleSamSpec : CounterSpec {
    fun other(): Int
}

fun multipleSamCapture(box: @Unique CaptureBox) {
    val counter = <!INTERFACE_AS_FUNCTION!>MultipleSamSpec<!> <!INVALID_UNIQUENESS_CAPTURE!>{ box.n++ }<!>
    println(box.n)
}

fun interface GenericCounter<T> {
    fun invoke(): T
    @GhostObserver @Pure fun value(): T = ghostObserver()
}

interface GenericBridge<T> : GenericCounter<T>
fun interface GenericIntCounter : GenericBridge<Int>

fun genericInheritedCapture(start: Int): @Unique GenericIntCounter {
    var n = start
    val counter: @Unique GenericIntCounter = GenericIntCounter {
        exposes(GenericIntCounter::class) { value() == n }
        ++n
    }
    println(<!INVALID_MOVED_ACCESS!>n<!>)
    return counter
}
