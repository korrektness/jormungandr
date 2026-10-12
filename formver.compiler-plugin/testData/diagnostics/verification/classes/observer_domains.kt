// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

interface Window<T> {
    @Pure @Unique fun <!VIPER_TEXT!>lower<!>(): Int
    @Pure @Unique fun <!VIPER_TEXT!>read<!>(index: Int, limit: Int, fallback: T): T
    @SpecOf("read") @Unique
    fun readSpec(index: Int, limit: Int, fallback: T) {
        preconditions { lower() <= index && index < limit }
        postconditions<T> { result -> result == fallback }
    }
}
interface ChildWindow<T> : Window<T>

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class TextWindow(var start: Int) : ChildWindow<String?> {
    @Pure @Unique override fun <!VIPER_TEXT!>lower<!>(): Int = start
    @Pure @Unique override fun <!VIPER_TEXT!>read<!>(at: Int, top: Int, text: String?): String? {
        preconditions { lower() - 1 <= at && at < top }
        postconditions<String?> { result -> result == text }
        return text
    }
}

fun <!VIPER_TEXT!>readWindow<!>(w: @Unique @Borrowed ChildWindow<String?>, i: Int, limit: Int): String? {
    preconditions { w.lower() <= i && i < limit }
    postconditions<String?> { result -> result == null && w.lower() == old(w.lower()) }
    return w.read(i, limit, null)
}

fun <!VIPER_TEXT!>windowRoundTrip<!>(w: @Unique TextWindow, i: Int, limit: Int) {
    preconditions { w.lower() <= i && i < limit }
    verify(w.read(w.lower() - 1, w.lower(), "edge") == "edge")
    val before = w.read(i, limit, "ok")
    val result = readWindow(w, i, limit)
    verify(result == null)
    verify(w.read(i, limit, "ok") == before)
    val view: @Unique ChildWindow<String?> = w
    verify(view.read(i, limit, "ok") == "ok")
}

fun <!VIPER_TEXT!>outsideWindow<!>(w: @Unique @Borrowed ChildWindow<String?>, limit: Int) {
    preconditions { w.lower() <= limit }
    <!VIPER_VERIFICATION_ERROR!>w.read(limit, limit, null)<!>
}

interface PositiveRead {
    @Pure fun <!VIPER_TEXT!>read<!>(i: Int): Int
    @SpecOf("read")
    fun readSpec(i: Int) {
        preconditions { i >= 0 }
        postconditions<Int> { result -> result >= i }
    }
}

class WiderRead : PositiveRead {
    @SpecOf("read")
    fun widerSpec(i: Int) {
        preconditions { i >= -1 }
        postconditions<Int> { result -> result == i + 1 }
    }
    @Pure override fun <!VIPER_TEXT!>read<!>(i: Int): Int = i + 1
}

class NarrowRead : PositiveRead {
    @Pure override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>read<!>(i: Int): Int {
        preconditions { i > 0 }
        postconditions<Int> { result -> result == i }
        return i
    }
}

class WrongRead : PositiveRead {
    @Pure override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>read<!>(i: Int): Int {
        preconditions { i >= 0 }
        postconditions<Int> { result -> result == i - 1 }
        return i - 1
    }
}

class WrongBody : PositiveRead {
    <!VIPER_VERIFICATION_ERROR!>@Pure override fun <!VIPER_TEXT!>read<!>(i: Int): Int = i - 1<!>
}

interface AnyRead {
    @Pure fun <!VIPER_TEXT!>read<!>(value: Any): Int
    @SpecOf("read") fun readSpec(value: Any) {
        postconditions<Int> { result -> result >= 0 }
    }
}

class NarrowTypeRead : AnyRead {
    @Pure override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>read<!>(value: Any): Int {
        preconditions { value is String }
        postconditions<Int> { result -> result == 0 }
        return 0
    }
}
