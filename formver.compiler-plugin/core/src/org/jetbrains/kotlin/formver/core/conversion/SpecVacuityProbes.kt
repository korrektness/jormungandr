/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedCallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.CustomGetter
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.names.AnonymousName
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.VacuityProbeName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Function
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type

/** The part of an external specification that a vacuity probe checks. */
enum class SpecVacuityPart { PRECONDITION, POSTCONDITION, LAWS }

/**
 * The probe of [part] of the specification of [model]. Each probe is its refutation's info and position, since
 * RefutePlugin matches a refutation to its outcome by position.
 */
data class SpecVacuityProbe(val model: FirBasedSymbol<*>, val part: SpecVacuityPart)

/**
 * Embeds [model], an external specification model, with probes for contradictions in what it specifies: the contract
 * of a callable that has a specification, and the laws a class declares.
 */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
fun ProgramConverter.registerVacuityProbes(model: FirBasedSymbol<*>) {
    when (model) {
        is FirNamedFunctionSymbol -> if (model.fir.userSpecification(session) != null) {
            vacuityProbes.registerCallable(model, (embedAnyFunction(model) as NamedCallableEmbedding).name)
        }
        is FirPropertySymbol -> if (model.userSpecification(session) != null) {
            // Only a getter that callers call carries a contract; the others are fields or built in.
            val getter = embedProperty(model).getter as? CustomGetter ?: return
            vacuityProbes.registerCallable(model, getter.getterMethod.name)
        }
        is FirRegularClassSymbol -> if (model.declarationSymbols.any { it is FirNamedFunctionSymbol && it.isLaw(session) }) {
            vacuityProbes.registerLaws(model, embedClass(model))
        }
        else -> throw SnaktInternalException(model.source, "$model is not a function, property or class model.")
    }
}

/**
 * Methods that assume a part of an external specification and then refute `false`. A refutation fails exactly when the
 * verifier can prove `false` there, that is, when the part it follows is contradictory.
 */
internal class SpecVacuityProbes {
    private val callables = mutableListOf<Pair<FirBasedSymbol<*>, SymbolicName>>()
    private val classes = mutableListOf<Pair<FirRegularClassSymbol, ClassTypeEmbedding>>()

    /** Probes the contract of [model], whose Viper method or function is called [name]. */
    fun registerCallable(model: FirBasedSymbol<*>, name: SymbolicName) {
        callables += model to name
    }

    /** Probes the laws of [model], which [type] embeds. */
    fun registerLaws(model: FirRegularClassSymbol, type: ClassTypeEmbedding) {
        classes += model to type
    }

    fun methods(methods: Collection<Method>, functions: Collection<Function>): List<Method> =
        callables.map { (model, name) ->
            methods.firstOrNull { it.name == name }?.let { return@map methodProbe(model, it) }
            functionProbe(model, checkNotNull(functions.firstOrNull { it.name == name }) { "$model has no Viper callable $name." })
        } + classes.map { (model, type) -> lawProbe(model, type) }

    private fun methodProbe(model: FirBasedSymbol<*>, callee: Method): Method {
        val call = Stmt.MethodCall(callee.name, callee.formalArgs.map(::use), callee.formalReturns.map(::use))
        return probe(VacuityProbeName(callee.name), callee.formalArgs, callee.formalReturns, callee.pres, model, call)
    }

    private fun functionProbe(model: FirBasedSymbol<*>, callee: Function): Method {
        val result = Declaration.LocalVarDecl(AnonymousName(0), callee.retType)
        val call = Stmt.LocalVarAssign(use(result), Exp.FuncApp(callee.name, callee.formalArgs.map(::use), callee.retType))
        // A termination measure is a function's own obligation, not a fact a caller assumes.
        val pres = callee.pres.filterNot { it is Exp.DecreasesWildcard || it is Exp.DecreasesTuple }
        return probe(VacuityProbeName(callee.name), callee.formalArgs, listOf(result), pres, model, call)
    }

    private fun probe(
        name: SymbolicName, args: List<Declaration.LocalVarDecl>, returns: List<Declaration.LocalVarDecl>, pres: List<Exp>,
        model: FirBasedSymbol<*>, call: Stmt,
    ): Method = object : Method(name) {
        override val formalArgs = args
        override val formalReturns = returns
        override val pres = pres
        override val body = Stmt.Seqn(listOf(
            refute(SpecVacuityProbe(model, SpecVacuityPart.PRECONDITION)),
            call,
            refute(SpecVacuityProbe(model, SpecVacuityPart.POSTCONDITION)),
        ))
    }

    // The laws sit in the body of the unique predicate, so only an unfolding exposes them.
    private fun lawProbe(model: FirRegularClassSymbol, type: ClassTypeEmbedding): Method = object : Method(VacuityProbeName(type.uniquePredicateName)) {
        private val receiver = Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref)
        private val owned = Exp.PredicateAccess(type.uniquePredicateName, listOf(use(receiver)), PermExp.FullPerm())
        override val formalArgs = listOf(receiver)
        override val formalReturns = emptyList<Declaration.LocalVarDecl>()
        override val pres = listOf<Exp>(owned)
        override val body = Stmt.Seqn(listOf(Stmt.Unfold(owned), refute(SpecVacuityProbe(model, SpecVacuityPart.LAWS))))
    }

    private fun refute(probe: SpecVacuityProbe): Stmt =
        Stmt.Refute(Exp.BoolLit(false), Position.Wrapped(probe), Info.Wrapped(probe))

    private fun use(declaration: Declaration.LocalVarDecl) = Exp.LocalVar(declaration.name, declaration.type)
}
