import org.jetbrains.kotlin.formver.plugin.AlwaysVerify


@AlwaysVerify
fun <!VIPER_TEXT!>testReturn<!>(): Int {
    return 0
    return 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnFromLoop<!>(): Int {
    while (true) {
        return 0
    }
    return 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>whileBreak<!>(b: Boolean): Int {
    var i = 0
    while (b) {
        i = 1
        break
    }
    return i
}

@AlwaysVerify
fun <!VIPER_TEXT!>whileContinue<!>() {
    var b = true
    while (b) {
        b = false
        continue
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>whileNested<!>(b: Boolean) {
    while(b){
        while (b){
            break
        }
        continue
        while (b){
            continue
        }
        break
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>labelledBreak<!>(b: Boolean) {
    loop1@ while(b){
        loop2@ while (b){
            break@loop1
            break@loop2
            break
        }
        break@loop1
        break
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>labelledContinue<!>(b: Boolean) {
    loop1@ while(b){
        loop2@ while (b){
            continue@loop1
            continue@loop2
            continue
        }
        continue@loop1
        continue
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>labelledWhileShadowing<!>(b: Boolean) {
    loop1@ while(b){
        loop1@ while (b){
            break@loop1
            continue@loop1
        }
        loop1@ while (b){
            break@loop1
            continue@loop1
        }
        break@loop1
        continue@loop1
    }
}
