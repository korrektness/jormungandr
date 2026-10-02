// LOCALITY_CHECK_ONLY
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique

class A(val size: Int)

@Pure
fun size(a: @Unique A): Int = a.size

@Pure
fun @Unique A.receiverSize(): Int = size

@Pure
fun identity(a: A): A = a

fun `pass local as unique argument of a pure function`(x: @Borrowed A): Int =
    size(x) + x.receiverSize()

fun `pass local as shared argument of a pure function`(x: @Borrowed A): A =
    identity(<!LOCALITY_MISMATCH!>x<!>)

@Pure
fun `return unique parameter of a pure function`(a: @Unique A): A =
    <!LOCALITY_MISMATCH!>a<!>

@Pure
fun `pass unique parameter of a pure function as shared argument`(a: @Unique A): A =
    identity(<!LOCALITY_MISMATCH!>a<!>)
