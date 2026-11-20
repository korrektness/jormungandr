// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// Bodies of custom accessors, verified against the method header their callers call.

fun <!VIPER_TEXT!>needsPositive<!>(d: Int): Int {
    preconditions { d > 0 }
    return d
}

<!VIPER_TEXT!>class SetterBody<!>(var value: Int) {
    var falseSetter: Int
        <!VIPER_TEXT!>get<!>() = 0
        <!VIPER_TEXT!>set<!>(v) { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>) }

    var violatingSetter: Int
        <!VIPER_TEXT!>get<!>() = 0
        <!VIPER_TEXT!>set<!>(v) { value = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(v)<!> }

    var guardedSetter: Int
        <!VIPER_TEXT!>get<!>() = 0
        <!VIPER_TEXT!>set<!>(v) {
            if (v > 0) value = needsPositive(v)
            refute(false)
        }
}

<!VIPER_TEXT!>class GetterBody<!>(var value: Int) {
    val falseGetter: Int
        <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); return 0 }

    val violatingGetter: Int
        <!VIPER_TEXT!>get<!>() = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(value)<!>

    val guardedGetter: Int
        <!VIPER_TEXT!>get<!>() {
            val v = value
            refute(false)
            return if (v > 0) needsPositive(v) else 1
        }
}

<!VIPER_TEXT!>class BackingField<!> {
    var violating: Int = 1
        <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); return field }
        <!VIPER_TEXT!>set<!>(v) { field = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(v)<!> }

    var guarded: Int = 1
        <!VIPER_TEXT!>get<!>() {
            refute(false)
            return field
        }
        <!VIPER_TEXT!>set<!>(v) {
            if (v > 0) field = needsPositive(v)
            refute(false)
        }
}

open <!VIPER_TEXT!>class OpenGetter<!> {
    open val value: Int
        <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); return 0 }
}

<!VIPER_TEXT!>class OverridingGetter<!> : OpenGetter() {
    override val value: Int
        <!VIPER_TEXT!>get<!>() = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(0)<!>
}

val SetterBody.violatingExtension: Int
    <!VIPER_TEXT!>get<!>() = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(value)<!>

var GetterBody.guardedExtension: Int
    <!VIPER_TEXT!>get<!>() {
        refute(false)
        return value
    }
    <!VIPER_TEXT!>set<!>(v) {
        if (v > 0) value = needsPositive(v)
        refute(false)
    }

val topLevelGetter: Int
    <!VIPER_TEXT!>get<!>() { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>); return 0 }

var topLevelViolatingSetter: Int
    <!VIPER_TEXT!>get<!>() = 1
    <!VIPER_TEXT!>set<!>(v) { <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>needsPositive(v)<!> }

var topLevelGuarded: Int
    <!VIPER_TEXT!>get<!>() {
        refute(false)
        return 1
    }
    <!VIPER_TEXT!>set<!>(v) {
        if (v > 0) needsPositive(v)
        refute(false)
    }

var topLevelCustomSetter: Int = 0
    <!VIPER_TEXT!>set<!>(v) { refute(false) }

var topLevelBackingField: Int = 0
    <!VERIFICATION_SKIPPED!>get<!>() = <!UNSUPPORTED_FEATURE!>field<!>

// Callers rely on the header only.
fun <!VIPER_TEXT!>setterCaller<!>(s: SetterBody, g: GetterBody) {
    s.violatingSetter = 0
    g.guardedExtension = 0
    refute(false)
}

fun <!VIPER_TEXT!>getterCaller<!>(g: GetterBody): Long {
    refute(false)
    return g.violatingGetter.toLong() + g.guardedExtension.toLong()
}

fun <!VIPER_TEXT!>topLevelCaller<!>(): Int {
    topLevelViolatingSetter = 0
    topLevelGuarded = 0
    val read = topLevelGuarded
    topLevelCustomSetter = 1
    val defaultRead = <!OPAQUE_CALL!>topLevelCustomSetter<!>
    refute(false)
    return topLevelGetter
}

// A read calls the getter, whose result is any value of its type.
fun <!VIPER_TEXT!>topLevelReadsDiffer<!>() {
    val a = topLevelGuarded
    val b = topLevelGuarded
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == b<!>)
}
