/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.ReturnTarget
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.LabelEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExistsEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.Return
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.embeddings.types.NothingTypeEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type

/**
 * The sites through which a method leaves normally: each `return` to [label], by its source, and the end of the body
 * at [end] when the body can reach it. A failing user postcondition is reported at the site it fails on. With one
 * site, the method postconditions name it. With more, the Int variable [ghost] holds the index of the site taken, and
 * each user postcondition is asserted once per site after the body.
 */
class ReturnSites private constructor(
    private val label: LabelEmbedding,
    private val returns: List<KtSourceElement?>,
    private val reachesEnd: Boolean,
    private val end: KtSourceElement?,
    private val ghost: SymbolicName?,
) {
    private val ghostVar: Exp.LocalVar? = ghost?.let { Exp.LocalVar(it, Type.Int) }

    private val sites: List<Pair<KtSourceElement?, KotlinCondition.ProofClause>> =
        returns.map { it to KotlinCondition.ProofClause.POSTCONDITION_AT_RETURN } +
            listOfNotNull((end to KotlinCondition.ProofClause.POSTCONDITION_AT_END).takeIf { reachesEnd })

    fun declare(ctx: LinearizationContext) {
        ghost?.let { ctx.addDeclaration(Declaration.LocalVarDecl(it, Type.Int)) }
    }

    /** Records that the method leaves through the `return` at [source], when [target] is the method's own. */
    fun leaveThroughReturn(target: ReturnTarget, source: KtSourceElement?, ctx: LinearizationContext) {
        if (target.label?.name != label.name) return
        val index = returns.indexOf(source)
        if (index < 0) throw SnaktInternalException(source, "A return of the method is missing from its return sites.")
        record(index, source, ctx)
    }

    /** Records that the method leaves at the end of the body labelled [bodyLabel], when it is the method's own. */
    fun leaveAtEnd(bodyLabel: LabelEmbedding, ctx: LinearizationContext) {
        if (bodyLabel.name == label.name && reachesEnd) record(returns.size, end, ctx)
    }

    private fun record(index: Int, source: KtSourceElement?, ctx: LinearizationContext) {
        val variable = ghostVar ?: return
        val position = source.asPosition
        ctx.addStatement { Stmt.LocalVarAssign(variable, Exp.IntLit(index), position) }
    }

    /** [postcondition], linearized as [exp], as the method states it: naming the only site, if there is one. */
    fun tagged(postcondition: ExpEmbedding, exp: Exp): Exp {
        val role = postcondition.userPostconditionRole ?: return exp
        val (source, clause) = sites.singleOrNull() ?: return exp
        val inner = (exp as? Exp.WithInfo)?.inner ?: exp
        return Exp.WithInfo(inner, role.copy(clause = clause, site = source.asPosition).asInfo)
    }

    /**
     * Asserts each user postcondition among [postconditions] once per site, reported at that site, when there is more
     * than one site. An existential is left to the method postcondition: its witness may be a term that only the
     * postconditions before it introduce, so a separate assert of it can fail where the postcondition holds.
     */
    fun assertPostconditions(postconditions: List<ExpEmbedding>, typeResolver: TypeResolver, ctx: LinearizationContext) {
        val variable = ghostVar ?: return
        for (postcondition in postconditions) {
            val role = postcondition.userPostconditionRole ?: continue
            if (postcondition.containsExists()) continue
            val clause = postcondition.pureToViper(toBuiltin = true, typeResolver)
            val clausePosition = (postcondition as WithPosition).source.asPosition
            sites.forEachIndexed { index, (source, siteClause) ->
                val checked = Exp.Implies(Exp.EqCmp(variable, Exp.IntLit(index)), clause, clausePosition)
                val siteRole = role.copy(clause = siteClause, site = source.asPosition)
                val stmt = Stmt.Assert(checked, clausePosition, siteRole.asInfo)
                ctx.addStatement { stmt }
            }
        }
    }

    companion object {
        /**
         * The return sites of a method whose body is [body] and whose returns jump to [label]; [end] is where the body
         * ends. [freshGhost] supplies the name of the site variable when there is more than one site.
         */
        fun of(body: ExpEmbedding, label: LabelEmbedding, end: KtSourceElement?, freshGhost: () -> SymbolicName): ReturnSites {
            val returns = body.returnSources(label, null).distinct().toList()
            val reachesEnd = body.type.pretype != NothingTypeEmbedding
            val count = returns.size + if (reachesEnd) 1 else 0
            return ReturnSites(label, returns, reachesEnd, end, if (count > 1) freshGhost() else null)
        }
    }
}

/** The role of a user postcondition, which is reported at the return site it fails on. */
val ExpEmbedding.userPostconditionRole: SourceRole.Proof?
    get() = ((this as? WithPosition)?.sourceRole as? SourceRole.Proof)
        ?.takeIf { it.clause == KotlinCondition.ProofClause.POSTCONDITION }

private fun ExpEmbedding.containsExists(): Boolean = this is ExistsEmbedding || children().any { it.containsExists() }

private fun ExpEmbedding.returnSources(label: LabelEmbedding, source: KtSourceElement?): Sequence<KtSourceElement?> =
    when (this) {
        is WithPosition -> inner.returnSources(label, this.source)
        is Return -> (if (target.label?.name == label.name) sequenceOf(source) else emptySequence()) +
            returnExp.returnSources(label, source)
        else -> children().flatMap { it.returnSources(label, source) }
    }
