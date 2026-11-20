// FULL_JDK

import java.time.Instant
import org.jetbrains.kotlin.formver.plugin.*

interface Reader {
    fun <!VIPER_TEXT!>read<!>(): Any
}

<!VIPER_TEXT!>class InstantReader<!>(val start: Instant) : Reader {
    override fun <!VIPER_TEXT!>read<!>(): Any = <!OPAQUE_CALL!>start.plusSeconds(1)<!>
}

<!VIPER_TEXT!>class Stamp<!>(val at: Instant)

<!VIPER_TEXT!>class Clock<!> {
    val started: Instant = <!OPAQUE_CALL!>Instant.now()<!>
}

fun <!VIPER_TEXT!>epochAfter<!>(now: Instant): Long = <!OPAQUE_CALL!><!OPAQUE_CALL!>now.plusSeconds(60)<!>.toEpochMilli()<!>

fun <!VIPER_TEXT!>javaResultChain<!>(): Long = <!OPAQUE_CALL!><!OPAQUE_CALL!><!OPAQUE_CALL!>Instant.now()<!>.plusSeconds(1)<!>.toEpochMilli()<!>

fun <!VIPER_TEXT!>stampNow<!>(): Stamp = Stamp(<!OPAQUE_CALL!>Instant.now()<!>)

fun <!VIPER_TEXT!>lengthOfProperty<!>(): Int = <!OPAQUE_CALL!>System.getProperty("user.home")<!>.length

fun <!VIPER_TEXT!>epochPerStep<!>(now: Instant, steps: Long): Long = <!DIVISION_BY_ZERO, INTEGER_OVERFLOW!><!OPAQUE_CALL!>now.toEpochMilli()<!> / steps<!>

fun <!VIPER_TEXT!>propertyMayBeNull<!>() {
    val home = <!OPAQUE_CALL!>System.getProperty("user.home")<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>home != null<!>)
}

fun <!VIPER_TEXT!>receiverUseMakesNonNull<!>(): Boolean {
    postconditions<Boolean> { it }
    val home = <!OPAQUE_CALL!>System.getProperty("user.home")<!>
    home.length
    return home != null
}

fun <!VIPER_TEXT!>countArguments<!>(vararg values: Int): Int = values.size

fun <!VIPER_TEXT!>javaVarargElement<!>(): Int = countArguments(<!OPAQUE_CALL!>Integer.getInteger("limit")<!>)

<!VIPER_TEXT!>object Tag<!> {
    operator fun <!VIPER_TEXT!>getValue<!>(thisRef: String, property: kotlin.reflect.KProperty<*>): Int = 1
}

val String.<!VIPER_TEXT!>tag<!>: Int by Tag

fun <!VIPER_TEXT!>delegatedReceiverMakesNonNull<!>(): Boolean {
    postconditions<Boolean> { it }
    val home = <!OPAQUE_CALL!>System.getProperty("user.home")<!>
    home.tag
    return home != null
}

fun <!VIPER_TEXT!>javaFieldWriteEvaluatesValueFirst<!>(p: java.awt.Point): Boolean {
    postconditions<Boolean> { it }
    val location = p.location
    var evaluated = false
    try {
        location.x = run {
            evaluated = true
            1
        }
    } catch (e: NullPointerException) {
    }
    return evaluated
}

fun String.<!VIPER_TEXT!>joinedWith<!>(other: String): String = other

fun <!VIPER_TEXT!>callEvaluatesArgumentsFirst<!>(): Boolean {
    postconditions<Boolean> { it }
    val home = <!OPAQUE_CALL!>System.getProperty("user.home")<!>
    var evaluated = false
    try {
        home.joinedWith(run {
            evaluated = true
            "/"
        })
    } catch (e: NullPointerException) {
    }
    return evaluated
}
