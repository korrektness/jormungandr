/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderUpdate
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.text
import org.jetbrains.kotlin.formver.common.SnaktException
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayInit
import org.jetbrains.kotlin.formver.core.embeddings.expression.InlineCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.UniqueValAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.NestedPredicates
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeInvariantEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.withAccessRole
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.names.FunctionResultVariableName
import org.jetbrains.kotlin.formver.core.names.ReturnVariableName
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Stmt

/**
 * A path the fold state can track: a root variable followed by `@Unique` properties.
 */
typealias OwnedPath = FoldPath<VariableEmbedding, PathStep>

/** The shape of an owned root's predicate where the uniqueness checker finds the root `Unique`. */
typealias OwnedShape = RootShape<VariableEmbedding, PathStep>

/**
 * The owned path this expression reads, or `null` when it is not a variable followed by reads of `@Unique` properties.
 * A `StringBuilder` update returns its receiver, so it reads its receiver's path, an [IntArrayInit] reads its array's,
 * and an [InlineCall] whose callee returns `@Unique` reads its return variable.
 */
fun ExpEmbedding.ownedPath(): OwnedPath? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> OwnedPath(exp)
    is StringBuilderUpdate -> exp.builder.ownedPath()
    is IntArrayInit -> exp.array.ownedPath()
    is InlineCall -> if (exp.returnsUnique) OwnedPath(exp.returnVariable) else null
    is FieldAccess -> if (exp.field.isUnique) exp.receiver.ownedPath()?.plus(exp.field) else null
    is UniqueValAccess -> exp.receiver.ownedPath()?.plus(exp.step)
    else -> null
}

/** The state of a [FoldState] at a program point, for joining at labels and branch merges. */
typealias FoldSnapshot = FoldTrie.Snapshot<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?>

/**
 * Raised when the linearized code needs a unique predicate that the fold state does not hold.
 * The converter reports it as a conversion error.
 */
class FoldStateException(source: KtSourceElement?, message: String) : SnaktException(source, message, null)

/** [this] as the Kotlin source writes it, in backticks. */
fun OwnedPath.render(): String {
    val root = when (val name = root.name) {
        is ReturnVariableName, FunctionResultVariableName -> return "the result" + fields.joinToString("") { ".${it.spelling}" }
        else -> name.sourceSpelling ?: "a temporary value"
    }
    return "`" + (listOf(root) + fields.map { it.spelling }).joinToString(".") + "`"
}

/** The first line of the source text of [site], in backticks, cut short when it is long. */
internal fun renderSite(site: KtSourceElement?): String? {
    val line = site?.text?.lineSequence()?.firstOrNull()?.trim() ?: return null
    return "`" + (if (line.length > MAX_SITE_LENGTH) line.take(MAX_SITE_LENGTH) + "..." else line) + "`"
}

private const val MAX_SITE_LENGTH = 40

private fun Absence<VariableEmbedding, PathStep, KtSourceElement?>.render(): String = when (this) {
    Absence.NeverHeld -> "was not tracked as owned before this point"
    is Absence.Released -> "was consumed" + (renderSite(site)?.let { " by $it" } ?: "")
    is Absence.Moved -> "was moved to ${target.render()}" + (renderSite(site)?.let { " by $it" } ?: "")
    is Absence.Forgotten -> "was dropped where control flow joins" + (renderSite(site)?.let { " at $it" } ?: "")
}

/** A message naming the path a failure needs and why the fold state does not hold it. */
private fun FoldFailure<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?>.message(): String =
    when (this) {
        is FoldFailure.NotHeld -> {
            val subject = if (absent == path) "it" else absent.render()
            "Ownership of ${path.render()} is needed here, but $subject ${why.render()}."
        }
        is FoldFailure.HoleBelow ->
            "Ownership of all of ${path.render()} is needed here, but ${hole.render()} ${why.render()}."
        is FoldFailure.NotASupertype ->
            "Ownership of ${path.render()} as `${cls.name.sourceSpelling}` is needed here, but `${cls.name.sourceSpelling}` " +
                "is not a supertype of its type."
    }

/**
 * The [FoldTrie] of the owned paths of a method body, emitting its folds and unfolds as Viper statements.
 *
 * Roots are keyed by variable name and fields by field name. Only paths whose static type is a class that is not
 * `@Manual` are tracked. Each operation is the [FoldTrie] operation of the same name.
 */
class FoldState(private val typeResolver: TypeResolver) {
    private val trie = FoldTrie<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?>(object : FoldHierarchy<VariableEmbedding, PathStep, ClassTypeEmbedding> {
        override fun rootClass(root: VariableEmbedding) = trackedClass(root.type)
        override fun fieldClass(field: PathStep) = trackedClass(field.type)
        override fun chainTo(cls: ClassTypeEmbedding, field: PathStep) =
            typeResolver.hierarchyPathTo(cls, field).toList()

        override fun chainBelow(cls: ClassTypeEmbedding, target: ClassTypeEmbedding): List<ClassTypeEmbedding>? {
            val chain = mutableListOf<ClassTypeEmbedding>()
            var current = cls
            while (current != target) {
                chain += current
                if (target in typeResolver.lookupSuperTypes(current.name)) return chain
                current = typeResolver.superClass(current) ?: return null
            }
            return chain
        }

        override fun rootKey(root: VariableEmbedding): Any = root.name
        override fun fieldKey(field: PathStep): Any = field.name
    })

    /** The class of the predicate a path of [type] holds, `null` when such a path is not tracked. */
    fun trackedClass(type: TypeEmbedding): ClassTypeEmbedding? =
        (type.pretype as? ClassTypeEmbedding)?.takeUnless { with(typeResolver) { it.isManual } }

    fun acquire(ctx: LinearizationContext, path: OwnedPath) = trie.acquire(ctx.foldSink(), path)

    fun holds(path: OwnedPath): Boolean = trie.holds(path)

    fun release(ctx: LinearizationContext, path: OwnedPath) = trie.release(ctx.foldSink(), path)

    fun open(ctx: LinearizationContext, path: OwnedPath, field: PathStep) = trie.open(ctx.foldSink(), path, field)

    fun openOwn(ctx: LinearizationContext, path: OwnedPath) = trie.openOwn(ctx.foldSink(), path)

    fun close(ctx: LinearizationContext, path: OwnedPath) = trie.close(ctx.foldSink(), path)

    fun refresh(ctx: LinearizationContext, path: OwnedPath) = trie.refresh(ctx.foldSink(), path)

    fun expose(ctx: LinearizationContext, path: OwnedPath, cls: ClassTypeEmbedding) =
        trie.expose(ctx.foldSink(), path, cls)

    fun refreshRetained(ctx: LinearizationContext, path: OwnedPath) = trie.refreshRetained(ctx.foldSink(), path)

    fun tidy(ctx: LinearizationContext, path: OwnedPath) = trie.tidy(ctx.foldSink(), path)

    fun transfer(ctx: LinearizationContext, src: OwnedPath, dst: OwnedPath) = trie.transfer(ctx.foldSink(), src, dst)

    fun normalize(ctx: LinearizationContext) = trie.normalize(ctx.foldSink())

    fun normalizeTo(ctx: LinearizationContext, shapes: List<OwnedShape>) = trie.normalizeTo(ctx.foldSink(), shapes)

    fun mergeShapes(
        a: FoldSnapshot,
        b: FoldSnapshot,
    ) = trie.mergeShapes(a, b)

    fun snapshot() = trie.snapshot()

    fun restore(snapshot: FoldSnapshot) =
        trie.restore(snapshot)

    fun join(ctx: LinearizationContext, a: FoldSnapshot, b: FoldSnapshot) = trie.join(a, b, ctx.source)

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
            folded = { path, cls -> cls.uniquePredicateAccessInvariant(typeResolver).withAccessRole(path.loopHeadRole()) },
            open = { path, _, opened, children -> OpenedPredicates(path, opened, children) },
        ) ?: return null
        return shape.root.type.flags.adjustInvariant(held).fillHole(shape.root)
    }

    /** The bodies of the predicates of [opened], the static class first, with [children] standing for their fields. */
    private inner class OpenedPredicates(
        private val path: OwnedPath,
        private val opened: List<ClassTypeEmbedding>,
        children: List<Pair<PathStep, TypeInvariantEmbedding?>>,
    ) : TypeInvariantEmbedding {
        private val children = children.associate { (field, held) -> field.name to held }

        override fun fillHole(exp: ExpEmbedding): ExpEmbedding = bodyOf(opened.first(), exp)

        private fun bodyOf(cls: ClassTypeEmbedding, exp: ExpEmbedding): ExpEmbedding =
            with(typeResolver) {
                cls.uniquePredicateBody(exp, object : NestedPredicates {
                    override fun ofField(step: PathStep, access: TypeInvariantEmbedding) =
                        if (step.name in children) children[step.name]
                        else access.withAccessRole((path + step).loopHeadRole())

                    override fun ofSuperType(type: ClassTypeEmbedding, access: TypeInvariantEmbedding) =
                        if (type in opened) TypeInvariantEmbedding { bodyOf(type, it) }
                        else access.withAccessRole(path.loopHeadRole())
                })
            }
    }
}

private fun OwnedPath.loopHeadRole() = SourceRole.Ownership(render(), SourceRole.Ownership.Site.LoopHead)

private fun LinearizationContext.foldSink() =
    object : FoldSink<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?> {
        override fun unfold(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            val info = SourceRole.Ownership(path.render(), SourceRole.Ownership.Site.Unfold).asInfo
            addGuarded(place) { Stmt.Unfold(hierarchyPredicateAccess(place.exp, cls, source, info), source.asPosition, info) }
        }

        override fun fold(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            val info = SourceRole.Ownership(path.render(), SourceRole.Ownership.Site.Fold).asInfo
            addGuarded(place) { Stmt.Fold(hierarchyPredicateAccess(place.exp, cls, source, info), source.asPosition, info) }
        }

        override fun refresh(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            val pos = source.asPosition
            val info = SourceRole.Ownership(path.render(), SourceRole.Ownership.Site.Havoc).asInfo
            val access = place.guards.foldRight<Exp, Exp>(hierarchyPredicateAccess(place.exp, cls, source, info)) { guard, inner ->
                Exp.Implies(guard, inner, pos)
            }
            addStatement { Stmt.Exhale(access, pos, info) }
            addStatement { Stmt.Inhale(access, pos) }
        }

        override fun refreshOwn(path: OwnedPath, cls: ClassTypeEmbedding) {
            val place = placeOf(path)
            val pos = source.asPosition
            val withoutSuperTypes = object : NestedPredicates {
                override fun ofSuperType(type: ClassTypeEmbedding, access: TypeInvariantEmbedding) = null
            }
            val body = with(typeResolver) { cls.uniquePredicateBody(embeddingOf(path), withoutSuperTypes) }
                .pureToViper(toBuiltin = true, typeResolver, source)
            val held = place.guards.foldRight(body) { guard, inner -> Exp.Implies(guard, inner, pos) }
            addStatement { Stmt.Exhale(held, pos, SourceRole.Ownership(path.render(), SourceRole.Ownership.Site.Havoc).asInfo) }
            addStatement { Stmt.Inhale(held, pos) }
        }

        override val site: KtSourceElement? get() = source

        override fun fail(failure: FoldFailure<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?>): Nothing =
            throw FoldStateException(source, failure.message())
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
    return path.fields.fold(Place(rootExp, guardsOf(rootExp, path.root.type))) { parent, step ->
        val exp = step.valueOf(parent.exp, source.asPosition)
        Place(exp, parent.guards + guardsOf(exp, step.type))
    }
}

/** The expression reading [path]. */
private fun embeddingOf(path: OwnedPath): ExpEmbedding =
    path.fields.fold<PathStep, ExpEmbedding>(path.root) { receiver, step -> step.valueOf(receiver) }

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
    state.restore(state.join(this, thenBlock.second, elseBlock.second))
    return thenBlock.first to elseBlock.first
}
