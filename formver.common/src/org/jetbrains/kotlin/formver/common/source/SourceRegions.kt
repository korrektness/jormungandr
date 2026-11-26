package org.jetbrains.kotlin.formver.common.source

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.util.IdentityHashMap

enum class RegionKind {
    FILE, FUNCTION, LAMBDA, GETTER, SETTER, DEFAULT_ARGUMENT,
    PRIMARY_CONSTRUCTOR, SECONDARY_CONSTRUCTOR, CONSTRUCTOR_DELEGATION, ENUM_ENTRY,
    SUPERTYPE_CALL, SUPERTYPE_DELEGATE, INIT_BLOCK, SCRIPT_STATEMENT,
    PROPERTY_INITIALIZER, PROPERTY_DELEGATE, BACKING_FIELD_INITIALIZER, DESTRUCTURING_INITIALIZER,
    OTHER_BODY, OTHER_INITIALIZER,
}

data class RegionSpan(val kind: RegionKind, val start: Int, val end: Int)

data class RegionId(val span: RegionSpan, val discriminator: Int) {
    fun reference(): String = "${span.kind}:${span.start}:${span.end}:$discriminator"
}

/** Assigns discriminators in owner PSI preorder and finds parents by anchor ancestry, including shared nodes. */
fun sourceRegions(file: KtFile): List<SourceRegion> {
    val anchors = IdentityHashMap<PsiElement, RegionId>()
    val occurrences = mutableMapOf<RegionSpan, Int>()
    val regions = mutableListOf<AnchoredRegion>()
    val pending = ArrayDeque<PsiElement>()
    val first = file.firstChild
    if (first != null) pending.addLast(first)
    while (pending.isNotEmpty()) {
        val element = pending.removeLast()
        if (element is KtElement) {
            for ((kind, node) in regionAnchors(element)) {
                val range = node.textRange
                val span = RegionSpan(kind, range.startOffset, range.endOffset)
                val index = occurrences[span] ?: 0
                occurrences[span] = index + 1
                val id = RegionId(span, index)
                regions.add(AnchoredRegion(id, element, node, anchors.put(node, id)))
            }
        }
        val sibling = element.nextSibling
        if (sibling != null) pending.addLast(sibling)
        val child = element.firstChild
        if (child != null) pending.addLast(child)
    }
    return regions.map { (id, owner, node, outerAtNode) ->
        val enclosing = outerAtNode ?: generateSequence(node.parent) { it.parent }.firstNotNullOfOrNull(anchors::get)
        SourceRegion(id, owner, enclosing)
    }
}

data class SourceRegion(val id: RegionId, val owner: KtElement, val parent: RegionId?)

private data class AnchoredRegion(val id: RegionId, val owner: KtElement, val node: PsiElement, val outerAtNode: RegionId?)

private fun regionAnchors(element: KtElement): List<Pair<RegionKind, PsiElement>> = buildList {
    fun anchor(kind: RegionKind, node: PsiElement?) {
        if (node != null) add(kind to node)
    }
    when (element) {
        is KtLambdaExpression -> anchor(RegionKind.LAMBDA, element)
        is KtFunctionLiteral -> Unit
        is KtNamedFunction -> anchor(RegionKind.FUNCTION, element.bodyExpression)
        is KtPropertyAccessor -> anchor(if (element.isGetter) RegionKind.GETTER else RegionKind.SETTER, element.bodyExpression)
        is KtPrimaryConstructor -> if (hasConstruction(element.getContainingClassOrObject())) {
            anchor(RegionKind.PRIMARY_CONSTRUCTOR, element)
        }
        is KtSecondaryConstructor -> if (hasConstruction(element.getContainingClassOrObject())) {
            anchor(RegionKind.SECONDARY_CONSTRUCTOR, element.bodyExpression ?: element.getConstructorKeyword())
            if (!element.hasImplicitDelegationCall()) anchor(RegionKind.CONSTRUCTOR_DELEGATION, element.getDelegationCall())
        }
        is KtEnumEntry -> if (hasConstruction(element)) anchor(RegionKind.ENUM_ENTRY, element.nameIdentifier)
        is KtClassOrObject -> if (hasConstruction(element) && element.hasPrimaryConstructor() && !element.hasExplicitPrimaryConstructor()) {
            anchor(RegionKind.PRIMARY_CONSTRUCTOR, element.nameIdentifier ?: element.getDeclarationKeyword() ?: element)
        }
        is KtSuperTypeCallEntry -> anchor(RegionKind.SUPERTYPE_CALL, element)
        is KtDelegatedSuperTypeEntry -> anchor(RegionKind.SUPERTYPE_DELEGATE, element.delegateExpression)
        is KtScriptInitializer -> anchor(RegionKind.SCRIPT_STATEMENT, element.body)
        is KtAnonymousInitializer -> anchor(RegionKind.INIT_BLOCK, element.body)
        is KtParameter -> {
            val constructor = element.ownerFunction as? KtPrimaryConstructor
            if (constructor?.getContainingClassOrObject()?.hasModifier(KtTokens.ANNOTATION_KEYWORD) != true) {
                anchor(RegionKind.DEFAULT_ARGUMENT, element.defaultValue)
            }
        }
        is KtProperty -> {
            if (!element.hasModifier(KtTokens.CONST_KEYWORD)) anchor(RegionKind.PROPERTY_INITIALIZER, element.initializer)
            anchor(RegionKind.PROPERTY_DELEGATE, element.delegateExpression)
        }
        is KtBackingField -> anchor(RegionKind.BACKING_FIELD_INITIALIZER, element.initializer)
        is KtDestructuringDeclaration -> anchor(RegionKind.DESTRUCTURING_INITIALIZER, element.initializer)
        is KtDeclarationWithBody -> anchor(RegionKind.OTHER_BODY, element.bodyExpression)
        is KtDeclarationWithInitializer -> anchor(RegionKind.OTHER_INITIALIZER, element.initializer)
    }
}

private fun hasConstruction(element: KtClassOrObject): Boolean =
    !element.hasModifier(KtTokens.ANNOTATION_KEYWORD) && (element !is KtClass || !element.isInterface()) &&
        generateSequence(element) { (it.parent as? KtClassBody)?.parent as? KtClassOrObject }
            .none { it.hasModifier(KtTokens.EXPECT_KEYWORD) }
