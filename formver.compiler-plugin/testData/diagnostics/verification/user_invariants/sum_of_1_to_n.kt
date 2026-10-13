// FULL_JDK


import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>recursiveSumOfIntegersUpToN<!>(n: Int): Int {
    preconditions { 0 <= n && n <= 32767 }
    postconditions<Int> { res ->
        0 <= res && res <= n * 32767
        res + res == n * (n + 1)
    }

    if (n == 0) return 0
    else return n + recursiveSumOfIntegersUpToN(n - 1)
}

fun <!VIPER_TEXT!>sumOfIntegersUpToN<!>(n: Int): Int {
    preconditions { 0 <= n && n <= 32767 }
    postconditions<Int> { res ->
        0 <= res && res <= n * 32767
        res + res == n * (n + 1)
    }

    var sum = 0
    var i = 0
    while (i < n) {
        loopInvariants {
            0 <= i && i <= n && i <= 32767
            0 <= sum && sum <= i * 32767
            sum + sum == i * (i + 1)
        }
        sum += i + 1
        ++i
    }
    return sum
}
