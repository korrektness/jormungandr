// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class GuardedCell<!>(var value: Int)

fun <!VIPER_TEXT!>guardedObservation<!>(enabled: Boolean, cell: @Unique @Borrowed GuardedCell?) {
    preconditions {
        enabled == (cell != null)
        // With enabled=false and cell=null, this clause needs no field permission.
        !enabled || <!NULL_ASSERTION_NOT_ESTABLISHED!>cell!!<!>.value >= 0
    }
}

fun <!VIPER_TEXT!>absentCell<!>() {
    guardedObservation(false, null)
}
