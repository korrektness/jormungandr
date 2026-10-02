/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderUpdate
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.SnaktException
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.NestedPredicates
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeInvariantEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Stmt

/**
 * A path the fold state can track: a root variable followed by `@Unique` fields.
 */
typealias OwnedPath = FoldPath<VariableEmbedding, FieldEmbedding>

/** The shape of an owned root's predicate where the uniqueness checker finds the root `Unique`. */
typealias OwnedShape = RootShape<VariableEmbedding, FieldEmbedding>

/**
 * The owned path this expression reads, or `null` when it is not a variable followed by reads of `@Unique` fields. A
 * `StringBuilder` update returns its receiver, so it reads its receiver's path.
 */
fun ExpEmbedding.ownedPath(): OwnedPath? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> OwnedPath(exp)
    is StringBuilderUpdate -> exp.builder.ownedPath()
    is FieldAccess -> if (exp.field.isUnique) exp.receiver.ownedPath()?.plus(exp.field) else null
    else -> null
}

/**
 * Raised when the linearized code needs a unique predicate that the fold state does not hold.
 * The converter reports it as a conversion error.
 */
class FoldStateException(source: KtSourceElement?, message: String) : SnaktException(source, message, null)

/**
 * The [FoldTrie] of the owned paths of a method body, emitting its folds and unfolds as Viper statements.
 *
 * Roots are keyed by variable name and fields by field name. Only paths whose static type is a class that is not
 * `@Manual` are tracked. Each operation is the [FoldTrie] operation of the same name.
 */
class FoldState(private val typeResolver: TypeResolver) {
    private val trie = FoldTrie(object : FoldHierarchy<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding> {
        override fun rootClass(root: VariableEmbedding) = trackedClass(root.type)
        override fun fieldClass(field: FieldEmbedding) = trackedClass(field.type)
        override fun chainTo(cls: ClassTypeEmbedding, field: FieldEmbedding) =
            typeResolver.hierarchyPathTo(cls, field).toList()

        override fun rootKey(root: VariableEmbedding): Any = root.name
        override fun fieldKey(field: FieldEmbedding): Any = field.name
    })

    private fun trackedClass(type: TypeEmbedding): ClassTypeEmbedding? =
        (type.pretype as? ClassTypeEmbedding)?.takeUnless { with(typeResolver) { it.isManual } }

    fun acquire(ctx: LinearizationContext, path: OwnedPath) = trie.acquire(ctx.foldSink(), path)

    fun holds(path: OwnedPath): Boolean = trie.holds(path)

    fun release(ctx: LinearizationContext, path: OwnedPath) = trie.release(ctx.foldSink(), path)

    fun open(ctx: LinearizationContext, path: OwnedPath, field: FieldEmbedding) = trie.open(ctx.foldSink(), path, field)

    fun openOwn(ctx: LinearizationContext, path: OwnedPath) = trie.openOwn(ctx.foldSink(), path)

    fun close(ctx: LinearizationContext, path: OwnedPath) = trie.close(ctx.foldSink(), path)

    fun refresh(ctx: LinearizationContext, path: OwnedPath) = trie.refresh(ctx.foldSink(), path)

    fun tidy(ctx: LinearizationContext, path: OwnedPath) = trie.tidy(ctx.foldSink(), path)

    fun transfer(ctx: LinearizationContext, src: OwnedPath, dst: OwnedPath) = trie.transfer(ctx.foldSink(), src, dst)

    fun normalize(ctx: LinearizationContext) = trie.normalize(ctx.foldSink())

    fun normalizeTo(ctx: LinearizationContext, shapes: List<OwnedShape>) = trie.normalizeTo(ctx.foldSink(), shapes)

    fun mergeShapes(
        a: FoldTrie.Snapshot<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding>,
        b: FoldTrie.Snapshot<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding>,
    ) = trie.mergeShapes(a, b)

    fun snapshot() = trie.snapshot()

    fun restore(snapshot: FoldTrie.Snapshot<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding>) =
        trie.restore(snapshot)

    fun join(
        a: FoldTrie.Snapshot<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding>,
        b: FoldTrie.Snapshot<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding>,
    ) = trie.join(a, b)

    fun jumpTo(ctx: LinearizationContext, label: SymbolicName) = trie.jumpTo(ctx.foldSink(), label)

    fun enterLoop(
        ctx: LinearizationContext,
        headLabel: SymbolicName,
        head: List<OwnedShape>,
        exitLabel: SymbolicName,
        exit: List<OwnedShape>,
    ): List<OwnedShape> = trie.enterLoop(ctx.foldSink(), headLabel, head, exitLabel, exit)

    fun arriveAt(ctx: LinearizationContext, label: SymbolicName) = trie.arriveAt(ctx.foldSink(), label)

    /**
     * The permissions [shape] holds, as an assertion: the root's predicate when it is folded, and otherwise the bodies
     * of the unfolded predicates, with the nested predicates of open children replaced in the same way and those of
     * holes left out. Nullable paths are guarded by their non-nullness.
     */
    fun permission(shape: OwnedShape): ExpEmbedding? {
        val held = trie.describe<TypeInvariantEmbedding>(
            shape,
            folded = { _, cls -> cls.uniquePredicateAccessInvariant(typeResolver) },
            open = { _, _, opened, children -> OpenedPredicates(opened, children) },
        ) ?: return null
        return shape.root.type.flags.adjustInvariant(held).fillHole(shape.root)
    }

    /** The bodies of the predicates of [opened], the static class first, with [children] standing for their fields. */
    private inner class OpenedPredicates(
        private val opened: List<ClassTypeEmbedding>,
        children: List<Pair<FieldEmbedding, TypeInvariantEmbedding?>>,
    ) : TypeInvariantEmbedding {
        private val children = children.associate { (field, held) -> field.name to held }

        override fun fillHole(exp: ExpEmbedding): ExpEmbedding = bodyOf(opened.first(), exp)

        private fun bodyOf(cls: ClassTypeEmbedding, exp: ExpEmbedding): ExpEmbedding =
            with(typeResolver) {
                cls.uniquePredicateBody(exp, object : NestedPredicates {
                    override fun ofField(field: FieldEmbedding, access: TypeInvariantEmbedding) =
                        if (field.name in children) children[field.name] else access

                    override fun ofSuperType(type: ClassTypeEmbedding, access: TypeInvariantEmbedding) =
                        if (type in opened) TypeInvariantEmbedding { bodyOf(type, it) } else access
                })
            }
    }
}

private fun LinearizationContext.foldSink() =
    object : FoldSink<VariableEmbedding, FieldEmbedding, ClassTypeEmbedding> {
        override fun unfold(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            addGuarded(place) { Stmt.Unfold(hierarchyPredicateAccess(place.exp, cls, source), source.asPosition) }
        }

        override fun fold(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            addGuarded(place) { Stmt.Fold(hierarchyPredicateAccess(place.exp, cls, source), source.asPosition) }
        }

        override fun refresh(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            val pos = source.asPosition
            val access = place.guards.foldRight<Exp, Exp>(hierarchyPredicateAccess(place.exp, cls, source)) { guard, inner ->
                Exp.Implies(guard, inner, pos)
            }
            addStatement { Stmt.Exhale(access, pos) }
            addStatement { Stmt.Inhale(access, pos) }
        }

        override fun fail(message: String): Nothing = throw FoldStateException(source, message)
    }

/**
 * A tracked path as Viper reads it. [guards] say that each nullable prefix of the path, the path itself included, is
 * not `null`, outermost first.
 */
private data class Place(val exp: Exp, val guards: List<Exp>)

private fun LinearizationContext.guardsOf(exp: Exp, type: TypeEmbedding): List<Exp> =
    if (type.isNullable) listOf(Exp.NeCmp(exp, RuntimeTypeDomain.nullValue(pos = source.asPosition), source.asPosition))
    else emptyList()

private fun LinearizationContext.placeOf(path: OwnedPath): Place {
    val rootExp = path.root.toViperExp(this)
    return path.fields.fold(Place(rootExp, guardsOf(rootExp, path.root.type))) { parent, field ->
        val exp = Exp.FieldAccess(parent.exp, field.toViper(), source.asPosition)
        Place(exp, parent.guards + guardsOf(exp, field.type))
    }
}

/** Add the statement [buildStmt] builds, nested in one `if` per guard of [place]. */
private fun LinearizationContext.addGuarded(place: Place, buildStmt: LinearizationContext.() -> Stmt) =
    addStatement {
        place.guards.foldRight(buildStmt()) { guard, inner ->
            Stmt.If(guard, Stmt.Seqn(listOf(inner)), Stmt.Seqn(), source.asPosition)
        }
    }

/**
 * Linearize the arms [thenArm] and [elseArm] of a branch into blocks, each starting from the current fold state, and
 * end both in the shapes they merge into: a root stays held when both arms hold it, with every hole either arm has.
 * The fold state afterwards is that of the merge.
 */
fun LinearizationContext.branchBlocks(
    thenArm: LinearizationContext.() -> Unit,
    elseArm: LinearizationContext.() -> Unit,
): Pair<Stmt.Seqn, Stmt.Seqn> {
    val state = foldState ?: return asBlock(thenArm) to asBlock(elseArm)
    state.normalize(this)
    val entry = state.snapshot()
    val arms = listOf(thenArm, elseArm).map { arm ->
        state.restore(entry)
        val block = asBlock {
            arm()
            state.normalize(this)
        }
        block to state.snapshot()
    }
    val shapes = state.mergeShapes(arms[0].second, arms[1].second) ?: return arms[0].first to arms[1].first
    val (thenBlock, elseBlock) = arms.map { (block, end) ->
        state.restore(end)
        val tail = asBlock { state.normalizeTo(this, shapes) }
        val merged = Stmt.Seqn(block.stmts + tail.stmts, block.scopedSeqnDeclarations + tail.scopedSeqnDeclarations)
        merged to state.snapshot()
    }
    state.restore(state.join(thenBlock.second, elseBlock.second))
    return thenBlock.first to elseBlock.first
}
