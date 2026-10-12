package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.lexer.KtTokens

internal fun diagnosticSource(offset: Int): KtSourceElement {
    val node = object : LighterASTNode {
        override fun getTokenType() = KtTokens.IDENTIFIER
        override fun getStartOffset() = offset
        override fun getEndOffset() = offset + 1
    }
    val tree = object : FlyweightCapableTreeStructure<LighterASTNode> {
        override fun getRoot() = node
        override fun getParent(node: LighterASTNode): LighterASTNode? = null
        override fun getChildren(node: LighterASTNode, into: Ref<Array<LighterASTNode>>): Int {
            into.set(emptyArray())
            return 0
        }
        override fun disposeChildren(nodes: Array<LighterASTNode>, count: Int) {}
        override fun toString(node: LighterASTNode): CharSequence = "x"
        override fun getStartOffset(node: LighterASTNode) = node.startOffset
        override fun getEndOffset(node: LighterASTNode) = node.endOffset
    }
    return KtLightSourceElement(node, offset, offset + 1, tree)
}
