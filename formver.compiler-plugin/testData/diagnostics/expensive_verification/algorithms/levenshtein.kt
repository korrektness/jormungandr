// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

// `levenshteinDistance` from ktoml (utils/Utils.kt), rewritten where the plugin lacks a
// feature: `for` loops become `while` loops, `IntArray(n) { it }` becomes `IntArray(n)`
// plus a loop, the `also` swap becomes a swap through a temporary, the three-argument
// `minOf` becomes `if`s, and `isEmpty()` becomes `length == 0`.
//
// Row `i` of the table bounds every entry by `max(j, i)`; `r <= a || r <= b` states
// `r <= max(a, b)`.
@AlwaysVerify
fun <!VIPER_TEXT!>levenshteinDistance<!>(first: String, second: String): Int {
    postconditions<Int> { result ->
        0 <= result
        result <= first.length || result <= second.length
    }
    when {
        first == second -> return 0
        first.length == 0 -> return second.length
        second.length == 0 -> return first.length
        else -> {
            // this is a generated else block
        }
    }

    val firstLen = first.length + 1
    val secondLen = second.length + 1
    var distance: @Unique IntArray = IntArray(firstLen)
    var newDistance: @Unique IntArray = IntArray(firstLen)

    var k = 0
    while (k < firstLen) {
        loopInvariants {
            0 <= k && k <= firstLen
            distance.size == firstLen
            newDistance.size == firstLen
            forAll<Int> { j -> (0 <= j && j < k) implies (distance[j] == j) }
        }
        distance[k] = k
        k++
        refute(false)
    }

    var i = 1
    while (i < secondLen) {
        loopInvariants {
            1 <= i && i <= secondLen
            distance.size == firstLen
            newDistance.size == firstLen
            forAll<Int> { j ->
                (0 <= j && j < firstLen) implies
                    (0 <= distance[j] && (distance[j] <= j || distance[j] <= i - 1))
            }
        }
        newDistance[0] = i
        var j = 1
        while (j < firstLen) {
            loopInvariants {
                1 <= j && j <= firstLen
                distance.size == firstLen
                newDistance.size == firstLen
                forAll<Int> { m ->
                    (0 <= m && m < firstLen) implies
                        (0 <= distance[m] && (distance[m] <= m || distance[m] <= i - 1))
                }
                newDistance[0] == i
                forAll<Int> { m ->
                    (0 <= m && m < j) implies
                        (0 <= newDistance[m] && (newDistance[m] <= m || newDistance[m] <= i))
                }
            }
            val costReplace = distance[j - 1] + (if (first[j - 1] == second[i - 1]) 0 else 1)
            val costInsert = distance[j] + 1
            val costDelete = newDistance[j - 1] + 1

            var cost = costInsert
            if (costDelete < cost) cost = costDelete
            if (costReplace < cost) cost = costReplace
            newDistance[j] = cost
            j++
            refute(false)
        }
        val swap: @Unique IntArray = distance
        distance = newDistance
        newDistance = swap
        i++
        refute(false)
    }
    refute(false)
    return distance[firstLen - 1]
}
