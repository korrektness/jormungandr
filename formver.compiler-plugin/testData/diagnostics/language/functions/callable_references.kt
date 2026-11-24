// FULL_JDK
// DIAGNOSTIC_KINDS
// WITH_STDLIB

import java.io.File
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>requirePositive<!>(x: Int): Int {
    preconditions { x > 0 }
    postconditions<Int> { it == x }
    return x
}

fun <!VIPER_TEXT!>aliasReference<!>() {
    val ref: (Int) -> Int = ::requirePositive
    val alias = ref
    val result = alias.invoke(1)
    verify(result == 1)
}

fun <!VIPER_TEXT!>badArgument<!>() {
    val ref = ::requirePositive
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>ref(0)<!>
}

fun <!VIPER_TEXT!>scopeBadArgument<!>() {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>0.let(block = ::requirePositive)<!>
}

<!VIPER_TEXT!>class Source<!>(val expected: String) {
    fun <!VIPER_TEXT!>isBundled<!>(path: String): Boolean {
        postconditions<Boolean> { it == (path == expected) }
        return path == expected
    }
}

fun <!VIPER_TEXT!>boundReference<!>(sources: Source, other: Source, path: String) {
    var receiver = sources
    val bound: (String) -> Boolean = receiver::isBundled
    receiver = other
    val answer = path.let(bound)
    verify(answer == (path == sources.expected))
}

<!VIPER_TEXT!>object ReferenceSingleton<!> {
    fun <!VIPER_TEXT!>argument<!>(value: Int): Int {
        postconditions<Int> { it == value }
        return value
    }
}

fun <!VIPER_TEXT!>boundSingletonReference<!>() {
    val ref = ReferenceSingleton::argument
    val result = ref(7)
    verify(result == 7)
}

<!VIPER_TEXT!>class Bundled<!>(private val sources: Source) {
    fun <!VIPER_TEXT!>contains<!>(path: String?): Boolean = path?.let(sources::isBundled) == true
}

fun <!VIPER_TEXT!>ownedSource<!>(): @Unique Source = Source("")

fun <!VERIFICATION_SKIPPED!>ownedCapture<!>() {
    val bound = <!UNSUPPORTED_FEATURE!>ownedSource()::isBundled<!>
    bound("")
}

fun <!VIPER_TEXT!>dropResult<!>() {
    val adapter: (Int) -> Unit = ::requirePositive
    val result: Any = adapter(1)
    verify(Unit == result)
}

<!VIPER_TEXT!>class ReferenceBox<!>(var value: Int)

fun <!VIPER_TEXT!>setReferenceBox<!>(box: ReferenceBox) {
    box.value = 1
}

fun <!VIPER_TEXT!>sharedScopeReceiver<!>(box: ReferenceBox) {
    box.let(::setReferenceBox)
}

fun <!VIPER_TEXT!>ownedReferenceBox<!>(): @Unique ReferenceBox = ReferenceBox(0)

fun <!VERIFICATION_SKIPPED!>ownedScopeReceiver<!>() {
    <!UNSUPPORTED_FEATURE!>ownedReferenceBox().let(::setReferenceBox)<!>
}

fun <!VERIFICATION_SKIPPED!>ownedWithReceiver<!>() {
    <!UNSUPPORTED_FEATURE!>with(receiver = ownedReferenceBox(), block = ::setReferenceBox)<!>
}

fun <!VERIFICATION_SKIPPED!>ownedDirectArgument<!>() {
    val ref = ::setReferenceBox
    <!UNSUPPORTED_FEATURE!>ref(ownedReferenceBox())<!>
}

fun <!VIPER_TEXT!>isNullArgument<!>(text: String?): Boolean {
    postconditions<Boolean> { it == (text == null) }
    return text == null
}

fun <!VIPER_TEXT!>nullableReferenceArguments<!>() {
    val ref = ::isNullArgument
    val direct = ref(null)
    val scoped = with(null, ::isNullArgument)
    verify(direct && scoped)
}

fun <!VIPER_TEXT!>firstReferenceArgument<!>(first: String, second: String): String {
    postconditions<String> { it == first }
    return first
}

fun <!VIPER_TEXT!>snapshotReferenceArguments<!>() {
    var first = "before"
    val ref = ::firstReferenceArgument
    val result = ref(first, run { first = "after"; first })
    verify(result == "before")
}

fun <!VIPER_TEXT!>throwingTarget<!>(x: Int) {
    throwsOnlyIf { x < 0 }
    if (x < 0) throw IllegalArgumentException()
}

fun <!VIPER_TEXT!>unexpectedThrow<!>(x: Int) {
    throwsOnlyIf { false }
    val ref = ::throwingTarget
    <!THROWS_CONDITION_NOT_ESTABLISHED!>ref(x)<!>
}

fun <!VIPER_TEXT!>allowedThrow<!>(x: Int) {
    throwsOnlyIf { x < 0 }
    x.let(::throwingTarget)
}

fun <!VIPER_TEXT!>pathFile<!>(path: String?): File? = path?.let(::File)

fun <!VIPER_TEXT!>nullableFilePath<!>(path: String?): File {
    throwsOnlyIf { false }
    val create: (String?) -> File = ::File
    <!THROWS_CONDITION_NOT_ESTABLISHED!>return create(path)<!>
}

fun <!VIPER_TEXT!>trimString<!>(text: String): String = text.let(String::trim)

fun <!VIPER_TEXT!>platformTrim<!>() {
    val text = <!OPAQUE_CALL!>System.getProperty("snakt.absent")<!>
    try { text.let(String::trim) } catch (e: Exception) { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>) }
}

fun <!VERIFICATION_SKIPPED!>lazyTrim<!>(text: String): Sequence<String> = <!OPAQUE_CALL!><!OPAQUE_CALL!>text.lineSequence()<!>.map(<!UNSUPPORTED_FEATURE!>String::trim<!>)<!>

fun <!VERIFICATION_SKIPPED!>unavailableTarget<!>(file: File): String = file.let(<!UNSUPPORTED_FEATURE!>File::getCanonicalPath<!>)

fun <!VERIFICATION_SKIPPED!>escapedReference<!>(): (Int) -> Int = <!UNSUPPORTED_FEATURE!>::requirePositive<!>
