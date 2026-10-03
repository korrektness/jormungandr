import org.jetbrains.kotlin.formver.plugin.*

@NeverConvert
inline fun applyToOne(f: (Int) -> Int): Int = f(1)

fun anonymousFunctionArgument(): Int {
    val y = applyToOne(fun(x: Int): Int { <!INTERNAL_ERROR!>return x<!> })
    return y
}
