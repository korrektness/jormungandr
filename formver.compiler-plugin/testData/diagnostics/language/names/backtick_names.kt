// FULL_JDK
package `odd-pkg`.`2nd`

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class `odd-name`<!>(val `optional-dependencies`: Int, val `a b`: Int)

fun <!VIPER_TEXT!>`a-b`<!>(): Int {
    postconditions<Int> { it == 1 }
    return 1
}

fun <!VIPER_TEXT!>`a+b`<!>(): Int {
    postconditions<Int> { it == 2 }
    return 2
}

fun <!VIPER_TEXT!>a_b<!>(): Int {
    postconditions<Int> { it == 3 }
    return 3
}

fun <!VIPER_TEXT!>`_@2d@b`<!>(): Int {
    postconditions<Int> { it == 4 }
    return 4
}

fun <!VIPER_TEXT!>`1st`<!>(`x$y`: Int): Int {
    postconditions<Int> { it == `x$y` }
    return `x$y`
}

fun <!VIPER_TEXT!>distinctNames<!>(`ä`: Int, `$`: Int) {
    val `odd value` = `odd-name`(5, `ä`)
    val `r-1` = `a-b`()
    val `r+2` = `a+b`()
    val r_3 = a_b()
    val `r 4` = `_@2d@b`()
    val `r$5` = `1st`(`$`)
    verify(
        `r-1` == 1,
        `r+2` == 2,
        r_3 == 3,
        `r 4` == 4,
        `r$5` == `$`,
        `odd value`.`optional-dependencies` == 5,
        `odd value`.`a b` == `ä`,
    )
}

fun <!VIPER_TEXT!>wrongName<!>() {
    val `r-1` = `a-b`()
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>`r-1` == 2<!>)
}
