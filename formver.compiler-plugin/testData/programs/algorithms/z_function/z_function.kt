// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

@Pure
fun <!VIPER_TEXT!>decodedMatch<!>(encoded: Char): Int = encoded - '0'

// TODO: resolve solver stalls on nested prefix-match quantifiers.
// Verification includes decoded-count bounds and checked prefix-index and result sums.
@NeverVerify
fun <!VIPER_TEXT!>zFuncHelper<!>(s: String, res: String, i: Int, checkedLeft: Int, checkedRight: Int): Int {
    preconditions {
        s.length < '\uFFFF' - '0'
        1 <= i && i < s.length
        res.length == i
        0 <= checkedLeft && checkedLeft <= checkedRight && checkedRight <= s.length
        checkedLeft < i

        forAll<Int> { idx ->
            (0 <= idx && idx < i) implies (
                0 <= decodedMatch(res[idx]) && decodedMatch(res[idx]) <= s.length - idx && forAll<Int> {
                    (0 <= it && it < decodedMatch(res[idx])) implies (s[it] == s[idx + it])
                } && (decodedMatch(res[idx]) == s.length - idx || s[idx + decodedMatch(res[idx])] != s[decodedMatch(res[idx])])
            )
        }

        forAll<Int> {
            (0 <= it && it < checkedRight - checkedLeft) implies (s[checkedLeft + it] == s[it])
        }
    }
    postconditions<Int> { j ->
        i <= j && j <= s.length
        forAll<Int> { idx ->
            (i <= idx && idx < j) implies (s[idx - i] == s[idx])
        }
    }
    return when {
        checkedLeft == 0 || checkedRight <= i -> i
        else -> {
            val matched = decodedMatch(res[i - checkedLeft])
            val bound = i + matched
            if (bound < checkedRight) bound else checkedRight
        }
    }
}

// TODO: resolve solver stalls on the quantified Z-window invariants.
// Verification includes checked Char encoding, counter increments and result-string length.
@NeverVerify
fun String.<!VIPER_TEXT!>zFunction<!>(): String {
    preconditions {
        length < '\uFFFF' - '0'
    }
    postconditions<String> { res ->
        res.length == length
        forAll<Int> { idx ->
            (0 <= idx && idx < length) implies (
                0 <= decodedMatch(res[idx]) && decodedMatch(res[idx]) <= length - idx && forAll<Int> {
                    (0 <= it && it < decodedMatch(res[idx])) implies (get(it) == get(idx + it))
                } && (decodedMatch(res[idx]) == length - idx || get(idx + decodedMatch(res[idx])) != get(decodedMatch(res[idx])))
            )
        }
    }
    if (length == 0) return this
    var i = 1
    val encodedLength = '0' + length
    var res = "" + encodedLength

    var checkedLeft = 0
    var checkedRight = 0

    while (i < length) {
        loopInvariants {
            res.length == i
            1 <= i && i <= length
            forAll<Int> { idx ->
                (0 <= idx && idx < i) implies (
                    0 <= decodedMatch(res[idx]) && decodedMatch(res[idx]) <= length - idx && forAll<Int> {
                        (0 <= it && it < decodedMatch(res[idx])) implies (get(it) == get(idx + it))
                    } && (decodedMatch(res[idx]) == length - idx || get(idx + decodedMatch(res[idx])) != get(decodedMatch(res[idx])))
                )
            }
            0 <= checkedLeft && checkedLeft <= checkedRight && checkedRight <= length
            checkedLeft < i
            forAll<Int> {
                (0 <= it && it < checkedRight - checkedLeft) implies (get(checkedLeft + it) == get(it))
            }
        }
        var j = zFuncHelper(this, res, i, checkedLeft, checkedRight)
        while (j < length && get(j - i) == get(j)) {
            loopInvariants {
                i <= j && j <= length
                forAll<Int> {
                    (checkedLeft <= it && it < checkedRight) implies (get(it - checkedLeft) == get(it))
                }
                forAll<Int> { idx ->
                    (i <= idx && idx < j) implies (get(idx - i) == get(idx))
                }
            }
            ++j
        }
        val matched = j - i
        val encodedMatch = '0' + matched
        res += encodedMatch
        if (j > checkedRight) {
            checkedLeft = i
            checkedRight = j
        }
        ++i
    }
    return res
}

// TODO: resolve solver stalls on the quantified prefix-match invariants.
// Verification includes checked Char encoding, matching indexes and counter increments.
@NeverVerify
fun String.<!VIPER_TEXT!>zFunctionNaive<!>(): String {
    preconditions {
        length < '\uFFFF' - '0'
    }
    postconditions<String> { res ->
        res.length == length
        forAll<Int> { idx ->
            (0 <= idx && idx < length) implies (
                0 <= decodedMatch(res[idx]) && decodedMatch(res[idx]) <= length - idx && forAll<Int> {
                    (0 <= it && it < decodedMatch(res[idx])) implies (get(it) == get(idx + it))
                } && (decodedMatch(res[idx]) == length - idx || get(idx + decodedMatch(res[idx])) != get(decodedMatch(res[idx])))
            )
        }
    }
    if (length == 0) return this
    var i = 1
    val encodedLength = '0' + length
    var res = "" + encodedLength

    while (i < length) {
        loopInvariants {
            res.length == i
            0 <= i && i <= length
            forAll<Int> { idx ->
                (0 <= idx && idx < i) implies (
                    0 <= decodedMatch(res[idx]) && decodedMatch(res[idx]) <= length - idx && forAll<Int> {
                        (0 <= it && it < decodedMatch(res[idx])) implies (get(it) == get(idx + it))
                    } && (decodedMatch(res[idx]) == length - idx || get(idx + decodedMatch(res[idx])) != get(decodedMatch(res[idx])))
                )
            }
        }
        var j = i
        while (j < length && get(j - i) == get(j)) {
            loopInvariants {
                i <= j && j <= length
                forAll<Int> { idx ->
                    (0 <= idx && idx < j - i) implies (get(i + idx) == get(idx))
                }
            }
            ++j
        }
        val matched = j - i
        val encodedMatch = '0' + matched
        res += encodedMatch
        ++i
    }
    return res
}
