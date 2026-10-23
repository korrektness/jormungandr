// FULL_JDK

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
