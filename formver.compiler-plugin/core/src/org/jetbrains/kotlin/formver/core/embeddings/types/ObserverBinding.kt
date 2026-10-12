/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.Is
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.viper.ast.*

/** An observer evaluated through a particular ownership presentation. */
class ObserverBinding(val view: ClassTypeEmbedding, val signature: NonInlineFunctionSignature, private val resolver: TypeResolver) {
    private val declaringType = signature.dispatchReceiver!!.type.pretype as ClassTypeEmbedding
    private val inheritedInterface = resolver.isInterface(view) && view != declaringType
    private val name = if (inheritedInterface) ScopedName(view.name.asScope(), signature.name) else signature.name

    fun trigger(args: List<Exp>, position: Position = Position.NoPosition): Exp.FuncApp = Exp.FuncApp(name, args, Type.Ref, position)

    fun application(args: List<Exp>, position: Position = Position.NoPosition): Exp {
        if (resolver.isInterface(view)) {
            val access = SourceRole.ObserverAccess(position).asInfo
            return Exp.Unfolding(
                Exp.PredicateAccess(view.uniquePredicateName, listOf(args.first()), PermExp.WildcardPerm(), position, access),
                trigger(args, position), position, access,
            )
        }
        val path = generateSequence(view, resolver::superClass).takeWhile { it != declaringType }.toList()
        return path.foldRight<ClassTypeEmbedding, Exp>(trigger(args, position)) { type, body ->
            Exp.Unfolding(Exp.PredicateAccess(type.uniquePredicateName, listOf(args.first()), PermExp.FullPerm()), body, position)
        }
    }

    fun domain(through: ObserverBinding = this, includeDeclaredTypes: Boolean = true): List<Exp> {
        val receiver = through.signature.dispatchReceiver!!.toLocalVarUse()
        val variableNames = signature.formalArgs.zip(through.signature.formalArgs).associate { (formal, actual) ->
            formal.name to actual.name
        }
        fun guard(exp: Exp): Exp = when (exp) {
            is Exp.PredicateAccess -> if (exp.formalArgs == listOf(receiver)) Exp.BoolLit(true) else {
                val available = Exp.PermissionCheck(exp)
                Exp.And(Exp.Old(available), available)
            }
            is Exp.Implies -> Exp.Implies(Exp.Old(exp.left), guard(exp.right))
            is Exp.And -> Exp.And(guard(exp.left), guard(exp.right))
            else -> Exp.Old(exp)
        }
        val preconditions = signature.preconditions.filter { pre ->
            includeDeclaredTypes || pre !is Is || signature.formalArgs.none { formal ->
                (pre.inner as? VariableEmbedding)?.name == formal.name && pre.comparisonType.runtimeType == formal.type.runtimeType
            }
        }
        return preconditions.pureToViper(toBuiltin = true, resolver,
            observerReceiver = through.view to receiver, observerFootprintOpen = false, variableNames = variableNames).map(::guard)
    }

    fun function(): UserFunction? {
        if (!inheritedInterface) return null
        val receiver = signature.dispatchReceiver!!.toLocalVarUse()
        val observerReceiver = view to receiver
        val pres = signature.preconditions.pureToViper(toBuiltin = true, resolver, observerReceiver = observerReceiver).map {
            if (it is Exp.PredicateAccess && it.formalArgs == listOf(receiver))
                Exp.PredicateAccess(view.footprintPredicateName,
                    listOf(receiver), it.perm)
            else it
        }
        return UserFunction(name, signature.formalArgs.map { it.toLocalVarDecl() }, Type.Ref, pres,
            signature.postconditions.pureToViper(toBuiltin = true, resolver, observerReceiver = observerReceiver), null)
    }
}
