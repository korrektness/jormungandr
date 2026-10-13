// FULL_JDK
// WITH_STDLIB
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

fun interface ReviewAcc {
    @GhostObserver @Pure fun <!VIPER_TEXT!>total<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @SpecOf("invoke") fun invokeSpec(amount: Int) {
        preconditions { amount >= 0 && total() >= 0 }
        postconditions<Int> { r -> total() == old(total()) + amount && r == total() }
    }
}

fun <!VIPER_TEXT!>wrap<!>(g: @Unique ReviewAcc): @Unique ReviewAcc {
    preconditions { g.total() >= 0 }
    postconditions<ReviewAcc> { r -> r.total() == old(g.total()) }
    return ReviewAcc { amount ->
        exposes(ReviewAcc::class) { total() == g.total() }
        g(amount)
    }
}

fun <!VIPER_TEXT!>wrapClient<!>(g: @Unique ReviewAcc) {
    preconditions { g.total() == 0 }
    val w: @Unique ReviewAcc = wrap(g)
    val r = w(2)
    verify(r == 2)
}

class ReviewOwned(var n: Int) {
    @Unique fun <!VIPER_TEXT!>implicitReceiver<!>(): @Unique ReviewAcc {
        preconditions { n >= 0 }
        return ReviewAcc { amount ->
            exposes(ReviewAcc::class) { total() == n }
            n += amount
            n
        }
    }
    @Unique fun <!VIPER_TEXT!>explicitReceiver<!>(): @Unique ReviewAcc {
        preconditions { n >= 0 }
        return ReviewAcc { amount ->
            exposes(ReviewAcc::class) { total() == this@ReviewOwned.n }
            n += amount
            n
        }
    }
}

<!VIPER_TEXT!>class ReviewHolder<!> {
    val action: @Unique ReviewAcc = ReviewAcc <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
        exposes(ReviewAcc::class) { total() == 0 }
        -5
    }<!>
}

fun <!VIPER_TEXT!>holderClient<!>(h: @Unique ReviewHolder) {
    preconditions { h.action.total() == 0 }
    val r = h.action(1)
    verify(r == 1)
}

<!VIPER_TEXT!>class ReviewCtorHolder<!>(val action: @Unique ReviewAcc = ReviewAcc <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
    exposes(ReviewAcc::class) { total() == 0 }
    -5
}<!>)

<!VIPER_TEXT!>class ReviewInitHolder<!> {
    val action: @Unique ReviewAcc
    init {
        action = ReviewAcc <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
            exposes(ReviewAcc::class) { total() == 0 }
            -5
        }<!>
    }
}

<!VIPER_TEXT!>class ReviewSecondaryHolder<!> {
    val action: @Unique ReviewAcc
    constructor() {
        action = ReviewAcc <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
            exposes(ReviewAcc::class) { total() == 0 }
            -5
        }<!>
    }
}

fun interface NamedCounterSpec {
    @GhostObserver @Pure @Unique fun <!VIPER_TEXT!>total<!>(): Int = ghostObserver()
    @Unique @Borrowed operator fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @Unique @Borrowed @SpecOf("invoke") fun invokeSpec(amount: Int) {
        preconditions { amount >= 0 && total() >= 0 }
        postconditions<Int> { r -> total() == old(total()) + amount && r == total() }
    }
}

class NamedReviewAcc(var n: Int) : NamedCounterSpec {
    @Pure @Unique override fun <!VIPER_TEXT!>total<!>(): Int = n
    @Unique @Borrowed override fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int {
        n += amount
        return n
    }
}

class BrokenNamedReviewAcc(var n: Int) : NamedCounterSpec {
    @Pure @Unique override fun <!VIPER_TEXT!>total<!>(): Int = n
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed override fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int = -7<!>
}

fun <!VIPER_TEXT!>callTwice<!>(f: @Unique @Borrowed NamedCounterSpec): Int {
    preconditions { f.total() == 0 }
    postconditions<Int> { r -> r == 2 && f.total() == 2 }
    f(1)
    return f(1)
}

fun <!VIPER_TEXT!>namedCounterClient<!>(n: @Unique @Borrowed NamedReviewAcc) {
    preconditions { n.n == 0 }
    val r = callTwice(n)
    verify(r == 2, n.n == 2)
}

interface OrdinarySharedAcc {
    @Pure fun <!VIPER_TEXT!>total<!>(): Int
    fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @SpecOf("invoke") fun invokeSpec(amount: Int) {
        postconditions<Int> { r -> <!OWNERSHIP_NOT_ESTABLISHED, OWNERSHIP_NOT_ESTABLISHED, OWNERSHIP_NOT_ESTABLISHED!>total()<!> == old(total()) + amount && r == total() }
    }
}

fun interface GhostSharedAcc {
    @GhostObserver @Pure fun <!VIPER_TEXT!>total<!>(): Int = ghostObserver()
    fun <!VIPER_TEXT!>invoke<!>(amount: Int): Int
    @SpecOf("invoke") fun invokeSpec(amount: Int) {
        postconditions<Int> { r -> <!OWNERSHIP_NOT_ESTABLISHED, OWNERSHIP_NOT_ESTABLISHED, OWNERSHIP_NOT_ESTABLISHED!>total()<!> == old(total()) + amount && r == total() }
    }
}

fun <!VIPER_TEXT!>ordinarySharedClient<!>(f: OrdinarySharedAcc) {
    val r1 = f.invoke(1)
    val r2 = f.invoke(1)
    verify(r2 == r1)
}

fun <!VIPER_TEXT!>ghostSharedClient<!>(f: GhostSharedAcc) {
    val r1 = f.invoke(1)
    val r2 = f.invoke(1)
    verify(r2 == r1)
}

val <!VIPER_TEXT!>topLevelBadAction<!>: @Unique ReviewAcc = ReviewAcc <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>{ amount ->
    exposes(ReviewAcc::class) { total() == 0 }
    -5
}<!>

<!VIPER_TEXT!>class ReviewRefinementHolder<!> {
    val action: @Unique ReviewAcc = ReviewAcc <!OVERRIDE_NOT_REFINING, OVERRIDE_NOT_REFINING!>{ amount ->
        exposes(ReviewAcc::class) { total() == 0 }
        postconditions<Int> { r -> r == -5 }
        -5
    }<!>
}

fun interface ReviewRead {
    @GhostObserver @Pure fun <!VIPER_TEXT!>initial<!>(): Int = ghostObserver()
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke") fun invokeSpec() {
        preconditions { initial() >= 0 }
        postconditions<Int> { r -> r == initial() }
    }
}

<!VIPER_TEXT!>class ReviewGoodCtor<!>(seed: Int, val action: @Unique ReviewRead = ReviewRead {
    exposes(ReviewRead::class) { initial() == seed }
    seed
})

<!VIPER_TEXT!>class ReviewGoodInit<!> {
    val action: @Unique ReviewAcc
    init {
        var n = 0
        action = ReviewAcc { amount ->
            exposes(ReviewAcc::class) { total() == n }
            n += amount
            n
        }
    }
}

fun interface ReviewLawful {
    @GhostObserver @Pure fun <!VIPER_TEXT!>initial<!>(): Int = ghostObserver()
    @Unique @Borrowed fun <!VIPER_TEXT!>invoke<!>(): Int
    @SpecOf("invoke") fun invokeSpec() {
        postconditions<Int> { r -> r == 1 }
    }
    @Law @Pure fun nonnegative(): Boolean = initial() >= 0
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class ReviewBrokenLawHolder<!> {
    val action: @Unique ReviewLawful = ReviewLawful {
        exposes(ReviewLawful::class) { initial() == -1 }
        1
    }
}<!>

fun (@Unique ReviewOwned).<!VIPER_TEXT!>extensionReceiver<!>(): @Unique ReviewAcc {
    preconditions { n >= 0 }
    return ReviewAcc { amount ->
        exposes(ReviewAcc::class) { total() == this@extensionReceiver.n }
        n += amount
        n
    }
}
