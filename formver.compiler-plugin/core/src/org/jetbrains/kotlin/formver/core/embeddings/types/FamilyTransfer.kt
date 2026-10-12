/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.callables.argumentTypeGuards
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Type

/** A trusted consuming change of presentation of one dynamic object's owned footprint. */
class FamilyTransfer(from: ClassTypeEmbedding, to: ClassTypeEmbedding, private val resolver: TypeResolver) {
    private fun observations(source: ObserverBinding, target: ObserverBinding, restoring: Boolean): Exp {
        val variables = source.signature.formalArgs.map { it.toLocalVarDecl() }
        val args = variables.map { Exp.LocalVar(it.name, it.type) }
        val current = target.application(args)
        val equality = Exp.EqCmp(current, Exp.Old(source.application(args)))
        // Receiver ownership is supplied by the transfer's precondition. Other preconditions delimit the observer domain.
        val targetTypes = source.signature.argumentTypeGuards(target.signature).pureToViper(toBuiltin = true, resolver)
        // The interface domain is expressed through the held view. Refinement establishes the restored implementation's domain.
        val interfaceDomain = if (restoring) emptyList() else target.domain(through = source, includeDeclaredTypes = false)
        val domain = (source.domain() + interfaceDomain + targetTypes).distinct()
            .fold<Exp, Exp>(Exp.BoolLit(true)) { acc, exp -> Exp.And(acc, exp) }
        val body = Exp.Implies(domain, equality)
        return if (variables.size == 1) body else
            Exp.Forall(variables.drop(1), listOf(Exp.Trigger(listOf(target.trigger(args)))), body)
    }

    private fun equalities(from: ClassTypeEmbedding, to: ClassTypeEmbedding, restoring: Boolean = false) =
        resolver.observerBindings(from, to).map { (concrete, abstract) ->
            if (restoring) observations(abstract, concrete, restoring = true) else observations(concrete, abstract, restoring = false)
        }

    val method: Method = object : Method(ScopedName(from.name.asScope(), ScopedName(to.name.asScope(), PredicateName("upcast")))) {
        override val formalArgs = listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref))
        override val formalReturns = emptyList<Declaration.LocalVarDecl>()
        private val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
        override val pres = listOf(Exp.PredicateAccess(from.uniquePredicateName, listOf(receiver), PermExp.FullPerm()))
        override val posts get() = listOf(Exp.PredicateAccess(to.uniquePredicateName, listOf(receiver), PermExp.FullPerm())) +
            equalities(from, to)
    }

    /** Restores a concrete presentation only for an object known to have that runtime type. */
    val restore: Method by lazy {
        object : Method(ScopedName(from.name.asScope(), ScopedName(to.name.asScope(), PredicateName("restore")))) {
            override val formalArgs = listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref))
            override val formalReturns = emptyList<Declaration.LocalVarDecl>()
            private val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
            override val pres = listOf(
                Exp.PredicateAccess(to.uniquePredicateName, listOf(receiver), PermExp.FullPerm()),
                RuntimeTypeDomain.isSubtype(RuntimeTypeDomain.typeOf(receiver), from.runtimeType),
            )
            override val posts get() = listOf(Exp.PredicateAccess(from.uniquePredicateName, listOf(receiver), PermExp.FullPerm())) +
                equalities(from, to, restoring = true)
        }
    }
}
