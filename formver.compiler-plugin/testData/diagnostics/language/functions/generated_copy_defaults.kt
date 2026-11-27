import org.jetbrains.kotlin.formver.plugin.*

data <!VIPER_TEXT!>class CopyFields<!><T>(val data: T, val children: Int = 0)

fun <!VIPER_TEXT!>replaceChildren<!>(p: CopyFields<String>, children: Int): CopyFields<String> =
    p.copy(children = children)

fun <!VIPER_TEXT!>replaceData<!>(p: CopyFields<String>, data: String): CopyFields<String> = p.copy(data)

fun <!VIPER_TEXT!>copyBothDefaults<!>(p: CopyFields<String>): CopyFields<String> = p.copy()

fun <!VIPER_TEXT!>receiverOnce<!>(p: CopyFields<String>, children: Int): CopyFields<String> {
    var stage = 0
    val q = run {
        verify(stage == 0)
        stage = 1
        p
    }.copy(children = run {
        verify(stage == 1)
        stage = 2
        children
    })
    verify(stage == 2)
    return q
}

fun <!VIPER_TEXT!>captureOriginalReceiver<!>(p: CopyFields<String>, other: CopyFields<String>): CopyFields<String> {
    var receiver = p
    return receiver.copy(children = run { receiver = other; 7 })
}

fun <!VIPER_TEXT!>reorderedExplicit<!>(p: CopyFields<String>, data: String): CopyFields<String> {
    var stage = 0
    return p.copy(children = run { stage = 1; 7 }, data = run { verify(stage == 1); data })
}

fun <!VIPER_TEXT!>mixedExplicit<!>(p: CopyFields<String>, data: String): CopyFields<String> =
    p.copy(data, children = 7)

fun CopyFields<String>.<!VIPER_TEXT!>copy<!>(flag: Boolean, children: Int = 42): Int = if (flag) children else 0

fun <!VIPER_TEXT!>ordinaryCopyOverload<!>(p: CopyFields<String>): Int = p.copy(flag = true)
