// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Box

class `reject unique on a property`(<!WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET!>@property:Unique<!> var box: Box)

fun `reject unique on a parameter`(<!WRONG_ANNOTATION_TARGET!>@Unique<!> box: Box) {}

fun `reject borrowed on a parameter`(<!WRONG_ANNOTATION_TARGET!>@Borrowed<!> box: Box) {}

fun <!WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET!>@receiver:Unique<!> Box.`reject unique on an extension receiver`() {}

<!WRONG_ANNOTATION_TARGET!>@Unique<!>
fun `reject unique on a function`(): Box = Box()

fun `reject unique on a local variable`() {
    <!WRONG_ANNOTATION_TARGET!>@Unique<!> val box: Box = Box()
}
