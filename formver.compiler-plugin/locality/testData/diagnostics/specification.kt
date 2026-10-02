// LOCALITY_CHECK_ONLY
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.exists
import org.jetbrains.kotlin.formver.plugin.forAll
import org.jetbrains.kotlin.formver.plugin.loopInvariants
import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.preconditions
import org.jetbrains.kotlin.formver.plugin.verify

class A(val size: Int)

fun share(x: A): Boolean = true

var sink: A? = null

fun `mention local in specifications`(x: @Borrowed A) {
    preconditions {
        x.size > 0
    }
    postconditions<Unit> {
        forAll<Int> { i -> i < x.size }
    }
    var i = 0
    while (i < x.size) {
        loopInvariants {
            exists<Int> { j -> j == x.size }
        }
        i++
    }
    verify(x.size > 0)
}

inline fun <T, R> T.apply1(f: (T) -> R): R = f(this)

fun `pass local to an inline function in a specification`(x: @Borrowed A) {
    preconditions {
        x.apply1 { it.size } > 0
        x.apply1 { it }.size > 0
    }
}

fun `pass local to an inline function outside a specification`(x: @Borrowed A) {
    <!LOCALITY_MISMATCH!>x<!>.apply1 { it.size }
}

fun `pass local as shared argument in a specification`(x: @Borrowed A) {
    postconditions<Unit> {
        share(<!LOCALITY_MISMATCH!>x<!>)
    }
}

fun `store local in a specification`(x: @Borrowed A) {
    preconditions {
        sink = <!LOCALITY_MISMATCH!>x<!>
        true
    }
}
