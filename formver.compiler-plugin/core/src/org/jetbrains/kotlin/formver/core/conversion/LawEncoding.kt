/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.embeddings.expression.PlaceholderVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.asTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.injection
import org.jetbrains.kotlin.formver.core.linearization.PureFunBodyLinearizer
import org.jetbrains.kotlin.formver.core.linearization.toLinearizable
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.formver.viper.ast.*

/** Registers one ownership-only proof obligation for this implementation's inherited laws. */
fun ProgramConverter.registerClassLaws(symbol: FirRegularClassSymbol) {
    lawEncoding.registerClass(embedClass(symbol), symbol)
}

/** Converts laws in abstract ownership and concrete representation presentations. */
internal class LawEncoding(private val program: ProgramConversionContext) {
    private val interfaces = linkedMapOf<ClassTypeEmbedding, List<FirNamedFunctionSymbol>>()
    private val classes = linkedMapOf<ClassTypeEmbedding, Pair<FirRegularClassSymbol, List<FirNamedFunctionSymbol>>>()
    private data class Implementation(val type: ClassTypeEmbedding, val symbol: FirRegularClassSymbol, val source: KtSourceElement?)
    private val implementations = linkedMapOf<ClassTypeEmbedding, Implementation>()
    val methods = mutableListOf<Method>()

    fun registerType(type: ClassTypeEmbedding, symbol: FirRegularClassSymbol) {
        if (!symbol.classKind.isInterface) {
            val laws = symbol.ownClassLaws(program.session)
            if (laws.isNotEmpty()) {
                program.typeResolver.registerClassLaws(type)
                classes[type] = symbol to laws
            }
            return
        }
        val laws = context(program.checkerContext) { symbol.interfaceLaws() }
        interfaces[type] = laws
    }

    fun registerClass(type: ClassTypeEmbedding, symbol: FirRegularClassSymbol, source: KtSourceElement? = symbol.source) {
        implementations[type] = Implementation(type, symbol, source)
    }

    @OptIn(SymbolInternals::class)
    private fun convertLaw(
        symbol: FirNamedFunctionSymbol,
        view: ClassTypeEmbedding,
        source: KtSourceElement? = symbol.source,
    ): Exp {
        val signature = with(program) { symbol.toFunctionSignature() }
        val receiver = PlaceholderVariableEmbedding(DispatchReceiverName, view.asTypeEmbedding(), isUnique = true)
        val resolver = InlineParameterResolver(
            mapOf(SubstitutedArgument.DispatchThis to receiver), symbol.name.asString(), signature.returnTarget,
        )
        val context = MethodConverter(
            program, signature.signature, resolver, program.scopeIndexProducer.getFresh(), OwnershipFrame.root(null, emptyList()),
        ).statementCtxt()
        val body = context.convertPureBody(symbol.fir)
        if (!body.isPure()) program.reportPurityViolation(symbol.source, "Impure law body")
        val linearizer = PureFunBodyLinearizer(source, typeResolver = program.typeResolver, lawView = view)
        body.toLinearizable(symbol.source).toViperUnusedResult(linearizer)
        return signature.signature.returns.type.injection.fromRef(linearizer.constructExpression())
    }

    fun prepare() {
        val preparedClasses = mutableSetOf<ClassTypeEmbedding>()
        while (true) {
            val entry = classes.entries.firstOrNull { it.key !in preparedClasses } ?: break
            preparedClasses += entry.key
            val (owner, laws) = entry.value
            val expansion = ClassLawExpansion(program, owner)
            val receiver = PlaceholderVariableEmbedding(DispatchReceiverName, entry.key.asTypeEmbedding(), isUnique = true)
            program.typeResolver.setClassLaws(entry.key, laws.map { law ->
                val expression = expansion.body(law, mapOf(SubstitutedArgument.DispatchThis to receiver))
                val signature = with(program) { law.toFunctionSignature() }
                signature.signature.returns.type.injection.fromRef(expression)
            })
        }
        val prepared = mutableSetOf<ClassTypeEmbedding>()
        while (true) {
            val entry = interfaces.entries.firstOrNull { it.key !in prepared } ?: break
            prepared += entry.key
            program.typeResolver.setInterfaceLaws(entry.key, entry.value.map { convertLaw(it, entry.key) })
        }
        for ((view, symbol, source) in implementations.values.toList()) {
            val laws = context(program.checkerContext) { symbol.interfaceLaws() }
            if (laws.isEmpty()) continue
            val assertions = laws.map { convertLaw(it, view, source) }
            val position = source.asPosition
            val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
            methods += object : Method(ScopedName(view.name.asScope(), PredicateName("laws")), position) {
                override val formalArgs = listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref))
                override val formalReturns = emptyList<Declaration.LocalVarDecl>()
                private val owned = Exp.PredicateAccess(view.uniquePredicateName, listOf(receiver), PermExp.FullPerm())
                override val pres = listOf(owned)
                // Expose the representation's pure facts while keeping ownership folded for observer calls.
                override val body = Stmt.Seqn(listOf(Stmt.Unfold(owned, position), Stmt.Fold(owned, position)) +
                    assertions.map { Stmt.Assert(it, position) })
            }
        }
    }
}
