// FULL_JDK
// REPLACE_STDLIB_EXTENSIONS

import org.jetbrains.kotlin.formver.plugin.*

@Pure
fun <!VIPER_TEXT!>checkedInt<!>(x: Any): Int {
    preconditions { x is Int }
    return x as Int
}

@Pure
fun <!VIPER_TEXT!>checkedNonNull<!>(x: Int?): Int {
    preconditions { x != null }
    return x!!
}

@Pure
fun <!VIPER_TEXT!>nullableTarget<!>(x: Any?): Int? {
    preconditions { x == null || x is Int }
    return x as Int?
}

@Pure
fun <!VIPER_TEXT!>safeInt<!>(x: Any?): Int = (x as? Int) ?: -1

fun <!VIPER_TEXT!>castControls<!>() {
    verify(checkedInt(4) == 4)
    verify(checkedNonNull(5) == 5)
    verify(nullableTarget(null) == null)
    verify(safeInt(null) == -1)
    verify(safeInt(6) == 6)
    verify(nullBranch(null) == 0)
    verify(nullBranch(7) == 7)
}

fun <!VIPER_TEXT!>castValueControl<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>safeInt(null) == 6<!>)
}

fun <!VIPER_TEXT!>safeFailureBranch<!>(x: Any) {
    preconditions { x !is Int }
    verify(safeInt(x) == -1)
}

fun <!VIPER_TEXT!>safeFailureControl<!>(x: Any) {
    preconditions { x !is Int }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>safeInt(x) == 0<!>)
}

@Pure
fun <!VIPER_TEXT!>nullBranch<!>(x: Int?): Int = if (x == null) 0 else x<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>

@Pure
fun <!VIPER_TEXT!>badCast<!>(x: Any): Int {
    preconditions { x !is Int }
    return <!CAST_NOT_ESTABLISHED!>x as Int<!>
}

@Pure
fun <!VIPER_TEXT!>badBang<!>(x: Int?): Int {
    preconditions { x == null }
    return <!NULL_ASSERTION_NOT_ESTABLISHED!>x!!<!>
}

@Pure
fun <!VIPER_TEXT!>discardedCast<!>(x: Any): Int {
    <!CAST_NOT_ESTABLISHED!>x as Int<!>
    return 0
}

@Pure
fun <!VIPER_TEXT!>discardedSafeOperand<!>(x: Any): Int {
    (<!CAST_NOT_ESTABLISHED!>x as Int<!>) as? Any
    return 0
}

@Pure
fun <!VIPER_TEXT!>guardedDiscard<!>(x: Any): Int {
    if (x is Int) (x <!USELESS_CAST!>as Int<!>) as? Any
    return 0
}

fun <!VIPER_TEXT!>badPureCaller<!>(x: Any) {
    preconditions { x !is Int }
    val result = badCast(x)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

fun <!VIPER_TEXT!>contractCasts<!>(x: Any): Int {
    preconditions { x is Int && (x <!USELESS_CAST!>as Int<!>) >= 0 }
    postconditions<Int> { r -> r == (x as Int) }
    verify((x as Int) >= 0)
    return x <!USELESS_CAST!>as Int<!>
}

fun <!VIPER_TEXT!>unguardedContract<!>(x: Any) {
    preconditions { (<!CAST_NOT_ESTABLISHED!>x as Int<!>) >= 0 }
}

fun <!VIPER_TEXT!>specificationFailure<!>(x: Any, n: Int?) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>(<!CAST_NOT_ESTABLISHED!>x as Int<!>) >= 0<!>)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!NULL_ASSERTION_NOT_ESTABLISHED!>n!!<!> >= 0<!>)
}

fun <!VERIFICATION_SKIPPED!>specificationComponentCast<!>(x: Any) {
    verify((<!UNSUPPORTED_FEATURE!>x <!UNCHECKED_CAST!>as Array<String><!><!>) === x)
}

fun <!VERIFICATION_SKIPPED!>specificationComponentSafeCast<!>(x: Any) {
    verify((<!UNSUPPORTED_FEATURE!>x <!UNCHECKED_CAST!>as? Array<String><!><!>) == null)
}

fun <!VIPER_TEXT!>loopCast<!>(x: Any) {
    preconditions { x is Int }
    var done = false
    while (!done) {
        loopInvariants { (x as Int) == (x <!USELESS_CAST!>as Int<!>) }
        done = true
    }
}

<!VIPER_TEXT!>class CastLaw<!>(val value: Any?) {
    @Law @Pure @Unique fun guarded(): Boolean = value !is Int || (value <!USELESS_CAST!>as Int<!>) >= Int.MIN_VALUE
}

open <!VIPER_TEXT!>class CastBound<!>
<!VIPER_TEXT!>class CastBox<!><T>

@Pure
@Suppress("UNCHECKED_CAST")
fun <T : CastBound> <!VIPER_TEXT!>boundedCast<!>(x: Any): Boolean {
    preconditions { x is CastBound }
    return (x as T) === x
}

@Pure
@Suppress("UNCHECKED_CAST")
fun <T : CastBound> <!VIPER_TEXT!>badBoundedCast<!>(x: Any): Boolean = (<!CAST_NOT_ESTABLISHED!>x as T<!>) === x

@Pure
@Suppress("UNCHECKED_CAST")
fun <!VIPER_TEXT!>parameterizedCast<!>(x: Any): Boolean {
    preconditions { x is CastBox<*> }
    return (x as CastBox<Int>) === x
}

@Pure
fun <!VIPER_TEXT!>primitiveArrayCast<!>(x: Any): Boolean {
    preconditions { x is IntArray }
    return (x as IntArray) === x
}

@Pure
@Suppress("UNCHECKED_CAST")
fun <!VERIFICATION_SKIPPED!>componentCast<!>(x: Any): Boolean = (<!UNSUPPORTED_FEATURE!>x as Array<String><!>) === x

@Pure
@Suppress("UNCHECKED_CAST")
fun <!VERIFICATION_SKIPPED!>componentSafeCast<!>(x: Any): Boolean = (<!UNSUPPORTED_FEATURE!>x as? Array<String><!>) == null

@Pure
fun <!VIPER_TEXT!>redundantArrayCast<!>(x: Array<String>?): Boolean {
    preconditions { x != null }
    return (x as Array<String>) === x
}

@Pure
fun <!VIPER_TEXT!>arrayBang<!>(x: Array<String>?): Boolean {
    preconditions { x != null }
    return x!! === x
}

open <!VIPER_TEXT!>class CastBase<!>(var count: Int)
<!VIPER_TEXT!>class CastSub<!>(count: Int) : CastBase(count) { var extra: Int = 0 }
<!VIPER_TEXT!>class CastHolder<!>(val base: @Unique CastBase?) {
    @Pure @Unique fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>receiverViewResult<!>(): CastBase? {
        val y = this <!USELESS_CAST!>as? CastHolder<!>
        <!EXIT_UNIQUENESS_INCONSISTENCY!>return <!UNSUPPORTED_OWNERSHIP!>y?.base<!><!>
    }
}

@Pure
fun <!VIPER_TEXT!>heldSafeCount<!>(b: @Unique CastBase?): Int = (b as? CastSub)?.count ?: -1

@Pure
fun <!VIPER_TEXT!>heldCheckedCount<!>(b: @Unique CastBase): Int {
    preconditions { b is CastSub }
    return (b as CastSub).count
}

@Pure
fun <!VIPER_TEXT!>heldUpcastCount<!>(s: @Unique CastSub): Int {
    postconditions<Int> { r -> r == (s as CastBase).count }
    return (s as CastBase).count
}

@Pure
fun <!VIPER_TEXT!>badHeldCount<!>(b: @Unique CastBase): Int = (<!CAST_NOT_ESTABLISHED!>b as CastSub<!>).count

@Pure
fun <!VIPER_TEXT!>heldNestedCount<!>(h: @Unique CastHolder): Int = (h.base as? CastSub)?.count ?: -1

@Pure
fun <!VIPER_TEXT!>heldNonNullCount<!>(b: @Unique CastBase?): Int {
    preconditions { b != null }
    return b!!.count
}

fun <!VIPER_TEXT!>heldCastObservations<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { heldSafeCount(b) == old(heldSafeCount(b)) }
    val first = heldSafeCount(b)
    val second = heldSafeCount(b)
    verify(first == second)
    verify(b !is CastSub || (b <!USELESS_CAST!>as? CastSub<!>)?.count == b.count)
    b.count = b.count
    keepCastBase(b)
}

fun <!VIPER_TEXT!>keepCastBase<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { b.count == old(b.count) }
}

fun <!VIPER_TEXT!>heldCastControls<!>() {
    val s: @Unique CastSub = CastSub(3)
    verify(heldSafeCount(s) == 3)
    verify(heldCheckedCount(s) == 3)
    verify(recursiveView(s) == 3)
    verify(heldNonNullCount(s) == 3)
    verify(heldUpcastCount(s) == 3)
    val h: @Unique CastHolder = CastHolder(s)
    verify(heldNestedCount(h) == 3)
    verify(nestedViewCount(h) == 3)
    verify((h.base as? CastSub)?.count == 3)
    verify(heldSafeCount(null) == -1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>(h.base as? CastSub)?.count == 4<!>)
}

fun <!VIPER_TEXT!>heldCastFailure<!>(b: @Unique CastBase?) {
    preconditions { b !is CastSub }
    verify(heldSafeCount(b) == -1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>heldSafeCount(b) == 0<!>)
}

@Pure
fun <!VERIFICATION_SKIPPED!>sharedCastRead<!>(b: CastBase): Int {
    preconditions { b is CastSub }
    return <!UNSUPPORTED_OWNERSHIP!>(b as CastSub).count<!>
}

@Pure
fun <!VERIFICATION_SKIPPED!>subtypeOnlyRead<!>(b: @Unique CastBase): Int {
    preconditions { b is CastSub }
    return <!UNSUPPORTED_OWNERSHIP!>(b as CastSub).extra<!>
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>borrowedCastResult<!>(b: @Unique CastBase): <!INVALID_PURE_REFERENCE_RESULT!>CastSub?<!> = <!EXIT_UNIQUENESS_INCONSISTENCY, LOCALITY_MISMATCH, UNSUPPORTED_OWNERSHIP!>b as? CastSub<!>

@Pure
fun <!VIPER_TEXT!>viewCount<!>(b: @Unique CastBase?): Int {
    postconditions<Int> { r -> r == heldSafeCount(b) }
    val t = b
    val y = t as? CastSub
    return y?.count ?: -1
}

@Pure
fun <!VIPER_TEXT!>viewPureArgument<!>(b: @Unique CastBase?): Int {
    val y = b as? CastSub
    return heldSafeCount(y)
}

@Pure
fun (@Unique CastBase).<!VIPER_TEXT!>receiverViewCount<!>(): Int {
    val y = this as? CastSub
    return y?.count ?: -1
}

@Pure
fun <!VIPER_TEXT!>nestedViewCount<!>(h: @Unique CastHolder): Int {
    preconditions { h.base != null }
    val t = h.base
    val y = t!!
    return y.count
}

fun <!VIPER_TEXT!>viewControls<!>(b: @Unique @Borrowed CastBase) {
    verify(viewCount(b) == heldSafeCount(b))
    val first = viewCount(b)
    val second = viewCount(b)
    verify(first == second)
    verify(viewPureArgument(b) == heldSafeCount(b))
    verify(b.receiverViewCount() == heldSafeCount(b))
    b.count = 7
    verify(viewCount(b) == (if (b is CastSub) 7 else -1))
    keepCastBase(b)
}

@Pure
fun <!VIPER_TEXT!>viewFailureBranch<!>(b: @Unique CastBase?): Boolean {
    val y = b as? CastSub
    return if (y == null) b !is CastSub else y === b
}

fun <!VIPER_TEXT!>viewNullControl<!>() {
    verify(viewCount(null) == -1)
    verify(viewFailureBranch(null))
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>viewCount(null) == 0<!>)
}

@Pure
fun <!VIPER_TEXT!>recursiveView<!>(b: @Unique CastBase): Int {
    preconditions { b is CastSub }
    val y = (b as? CastSub)!!
    return y.count
}

@Pure
fun <!VIPER_TEXT!>badViewCast<!>(b: @Unique CastBase): Int {
    val y = <!CAST_NOT_ESTABLISHED!>b as CastSub<!>
    return y.count
}

@Pure
fun <!VIPER_TEXT!>badViewBang<!>(b: @Unique CastBase?): Int {
    val y = <!NULL_ASSERTION_NOT_ESTABLISHED!>b!!<!>
    return y.count
}

@Pure
fun <!VIPER_TEXT!>sharedViewResult<!>(b: CastBase): CastSub? {
    val y = b as? CastSub
    var z: CastSub? = null
    z = y
    return z
}

fun <!VIPER_TEXT!>sharedViewControl<!>(b: CastBase) {
    verify(sharedViewResult(b) === (b as? CastSub))
}

@Pure
fun <!VERIFICATION_SKIPPED!>viewSubtypeRead<!>(b: @Unique CastBase): Int {
    preconditions { b is CastSub }
    val y = b as CastSub
    return <!UNSUPPORTED_OWNERSHIP!>y.extra<!>
}

@Pure
fun <!VERIFICATION_SKIPPED!>sharedViewRead<!>(b: CastBase): Int {
    val y = b as? CastSub
    return <!UNSUPPORTED_OWNERSHIP!>y?.count<!> ?: 0
}

@Pure
fun <!VIPER_TEXT!>viewPair<!>(a: @Unique CastBase?, b: @Unique CastBase?): Boolean = a === b

@Pure
fun <!VIPER_TEXT!>holderPair<!>(h: @Unique CastHolder, b: @Unique CastBase?): Boolean = h.base === b

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>viewDuplicate<!>(b: @Unique CastBase): Boolean {
    val t = b
    val y = t as? CastSub
    return viewPair(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>b<!>, <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>y<!>)
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>viewOverlap<!>(h: @Unique CastHolder): Boolean {
    val y = h.base as? CastSub
    return holderPair(<!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>h<!>, <!INVALID_OVERLAPPING_UNIQUE_ARGUMENTS!>y<!>)
}

<!VIPER_TEXT!>class MutableCastHolder<!>(var base: @Unique CastBase?)

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>mutableView<!>(h: @Unique MutableCastHolder): Int {
    val y = <!UNSUPPORTED_OWNERSHIP!>h.base as? CastSub<!>
    return y?.count ?: 0
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>mutableLocalView<!>(h: @Unique MutableCastHolder): Int {
    val t = h.base
    val y = <!UNSUPPORTED_OWNERSHIP!>t as? CastSub<!>
    return y?.count ?: 0
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>multiSourceView<!>(a: @Unique CastBase?, b: @Unique CastBase?, c: Boolean): Int {
    val t = if (c) a else b
    val y = <!UNSUPPORTED_OWNERSHIP!>t!!<!>
    return y.count
}

@Pure
fun <!VIPER_TEXT!>plainMutableAlias<!>(h: @Unique MutableCastHolder): Int {
    val t = h.base
    return t?.count ?: 0
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>viewAssignment<!>(b: @Unique CastBase): Boolean {
    val y = b as? CastSub
    var z: CastBase? = null
    z = <!LOCALITY_MISMATCH, UNSUPPORTED_OWNERSHIP!>y<!>
    <!EXIT_UNIQUENESS_INCONSISTENCY!>return z == null<!>
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>viewConditional<!>(b: @Unique CastBase, c: Boolean): Boolean {
    val y = b as? CastSub
    val z = if (c) <!UNSUPPORTED_OWNERSHIP!>y<!> else b
    return z != null
}

@Pure
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>viewResult<!>(b: @Unique CastBase): <!INVALID_PURE_REFERENCE_RESULT!>CastSub?<!> {
    val y = b as? CastSub
    <!EXIT_UNIQUENESS_INCONSISTENCY!>return <!LOCALITY_MISMATCH, UNSUPPORTED_OWNERSHIP!>y<!><!>
}

@Pure
@Suppress("NOTHING_TO_INLINE")
inline fun <!VIPER_TEXT!>readViewInline<!>(b: @Borrowed CastBase?): Boolean = b == null

fun <!VERIFICATION_SKIPPED!>viewInline<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>; readViewInline(<!UNSUPPORTED_OWNERSHIP!>y<!>) }
}

fun <!VERIFICATION_SKIPPED!>viewInlineReceiver<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>; <!UNSUPPORTED_OWNERSHIP!>y<!>.let { it?.count ?: 0 } == 0 }
}

fun <!VERIFICATION_SKIPPED!>viewInlineIdentity<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>; val z = <!UNSUPPORTED_OWNERSHIP!>y<!>.let { it }; z == null }
}

fun <!VIPER_TEXT!>sharedInlineView<!>(b: CastBase): Boolean {
    postconditions<Boolean> { r -> val y = b as? CastSub; r == y.let { it === b } }
    return b is CastSub
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>inlineRootLaundering<!>(b: @Unique @Borrowed CastBase, c: Boolean): Boolean {
    postconditions<Boolean> { r ->
        r == b.let {
            val y = it as? CastSub
            val z = if (c) <!UNSUPPORTED_OWNERSHIP!>y<!> else null
            z == null
        }
    }
    return !c || b !is CastSub
}

fun <!VIPER_TEXT!>lexicalInlineView<!>(b: @Unique @Borrowed CastBase): Int {
    postconditions<Int> { r -> val y = b as? CastSub; r == run { y?.count ?: -1 } }
    return heldSafeCount(b)
}

fun <!VERIFICATION_SKIPPED!>lexicalInlineEscape<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> {
        <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>
        val z = run { <!UNSUPPORTED_OWNERSHIP!>y<!> }
        z == null
    }
}

fun <!VERIFICATION_SKIPPED!>quantifiedViewLocal<!>(b: @Unique @Borrowed CastBase) {
    preconditions {
        forAll<Int> { i ->
            <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>
            i != 0 || y == null || y.count >= 0
        }
    }
}

fun <!VIPER_TEXT!>guardedViewClause<!>(b: @Unique @Borrowed CastBase?) {
    preconditions { b != null; val y = b!!; y.count == 0 }
    postconditions<Unit> { b != null; val y = b!!; y.count == old(y.count) }
}

fun <!VIPER_TEXT!>stableViewOld<!>(h: @Unique @Borrowed CastHolder) {
    preconditions { h.base == null || h.base.count <= 0 }
    postconditions<Unit> {
        val y = h.base as? CastSub
        y == null || y.count == old(y.count) + 1
    }
    if (h.base is CastSub) h.base.count = h.base.count + 1
}

fun <!VIPER_TEXT!>stableViewOldControl<!>(h: @Unique @Borrowed CastHolder) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions { h.base == null || h.base.count <= 0 }
    postconditions<Unit> {
        val y = h.base as? CastSub
        y == null || y.count == old(y.count) + 2
    }
    if (h.base is CastSub) h.base.count = h.base.count + 1
}<!>

fun <!VERIFICATION_SKIPPED!>viewCapture<!>(b: @Unique @Borrowed CastBase) {
    postconditions<Unit> { <!UNSUPPORTED_FEATURE!>val y = b as? CastSub<!>; val f = { <!UNSUPPORTED_OWNERSHIP!>y<!> }; f() == null }
}

interface CastFace
<!VIPER_TEXT!>class CastConcrete<!>(var count: Int) : CastFace

@Pure
fun <!VERIFICATION_SKIPPED!>interfaceCastRead<!>(b: @Unique CastFace): Int {
    preconditions { b is CastConcrete }
    return <!UNSUPPORTED_OWNERSHIP!>(b as CastConcrete).count<!>
}
