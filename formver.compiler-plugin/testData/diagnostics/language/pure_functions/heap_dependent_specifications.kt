// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Node<!>(val value: Int, val next: Node?)

@Pure
fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isAlternating<!>(node: Node, expectsPositive: Boolean): Boolean {
    val isCurrentValid = if (expectsPositive) {
        node.value > 0
    } else {
        node.value < 0
    }
    val nextNode = node.next
    return isCurrentValid && if (nextNode != null) {
        isAlternating(nextNode, !expectsPositive)
    } else {
        true
    }
}

@Pure
fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isStrictlyAscendingAndPositive<!>(node: Node): Boolean {
    preconditions {
        node.value >= 0
    }
    val nextNode = node.next
    return if (nextNode != null) {
        if (nextNode.value < 0) {
            false
        } else {
            nextNode.value > node.value && isStrictlyAscendingAndPositive(nextNode)
        }
    } else {
        true
    }
}


<!VIPER_TEXT!>class TreeNode<!>(val value: Int, val left: TreeNode?, val right: TreeNode?)

@Pure
fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isLocallyValidBST<!>(node: TreeNode): Boolean {
    val leftNode = node.left
    val leftValid = if (leftNode != null) {
        leftNode.value < node.value && isLocallyValidBST(leftNode)
    } else {
        true
    }
    val rightNode = node.right
    val rightValid = if (rightNode != null) {
        rightNode.value > node.value && isLocallyValidBST(rightNode)
    } else {
        true
    }
    return leftValid && rightValid
}
