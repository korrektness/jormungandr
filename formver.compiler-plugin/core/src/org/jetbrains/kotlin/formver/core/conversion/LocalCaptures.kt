/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.callables.SpecialFunctions
import org.jetbrains.kotlin.formver.core.linearization.subExps
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyAccessEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.ParameterScope
import org.jetbrains.kotlin.formver.core.names.PretypeName
import org.jetbrains.kotlin.formver.viper.ast.Exp

private fun FirElement.localUses(): Pair<Set<FirVariableSymbol<*>>, Set<FirBasedSymbol<*>>> {
    val declared = mutableSetOf<FirBasedSymbol<*>>()
    val used = linkedSetOf<FirVariableSymbol<*>>()
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirVariable) declared += element.symbol
            if (element is FirQualifiedAccessExpression) when (val symbol = element.calleeReference.symbol) {
                is FirValueParameterSymbol -> used += symbol
                is FirPropertySymbol -> if (symbol.isLocal && !symbol.name.isSpecial) used += symbol
                else -> Unit
            }
            element.acceptChildren(this)
        }
    })
    return used to declared
}

/**
 * The parameters and locals declared outside [this] local function or class that its code reads or writes, in order of
 * first use.
 */
fun FirDeclaration.capturedLocals(): List<FirVariableSymbol<*>> {
    val (used, declared) = localUses()
    return used.filterNot { it in declared }
}

/**
 * The receivers of enclosing declarations that code of [this] local function or class reads, with the type of each, in
 * order of first use. A receiver of a class or function declared inside [this] is its own.
 */
fun FirElement.capturedReceivers(): Map<FirBasedSymbol<*>, ConeKotlinType> {
    val own = mutableSetOf<FirBasedSymbol<*>>()
    val used = linkedMapOf<FirBasedSymbol<*>, ConeKotlinType>()
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirDeclaration) own += element.symbol
            if (element is FirThisReceiverExpression) {
                val symbol = element.calleeReference.boundSymbol
                val receiver = symbol is FirClassSymbol<*> && symbol.classKind != ClassKind.OBJECT || symbol is FirReceiverParameterSymbol
                if (receiver) used.getOrPut(symbol!!) { element.resolvedType }
            }
            element.acceptChildren(this)
        }
    })
    return used.filterKeys { symbol ->
        val owner = if (symbol is FirReceiverParameterSymbol) symbol.containingDeclarationSymbol else symbol
        owner !in own
    }
}

/** The receivers that [this] reads and that neither it nor a class containing it declares. */
private fun FirDeclaration.enclosingReceiverReads(): Set<FirBasedSymbol<*>> {
    val containing = generateSequence((symbol as? FirCallableSymbol<*>)?.callableId?.classId) { it.outerClassId }.toSet()
    return capturedReceivers().keys.filterNotTo(mutableSetOf()) { it is FirClassSymbol<*> && it.classId in containing }
}

/** Whether [this] is a local function or class, or a member of a local class. */
fun FirDeclaration.isLocallyScoped(): Boolean = when (val symbol = symbol) {
    is FirCallableSymbol<*> -> symbol.callableId?.let { it.isLocal || it.classId?.isLocal == true } == true
    is FirClassLikeSymbol<*> -> symbol.classId.isLocal
    else -> false
}

/** Whether the `preconditions`, `postconditions` or `throwsOnlyIf` block of [this] reads a value that it captures. */
fun FirFunction.contractReadsCapture(): Boolean {
    if (!isLocallyScoped()) return false
    val statements = body?.statements ?: return false
    val specification = extractFirSpecification(statements, returnTypeRef.coneType)
    val blocks = listOfNotNull(specification.precond, specification.postcond, specification.throwsOnlyIf)
    if (blocks.isEmpty()) return false
    val captured = capturedLocals().toSet<FirBasedSymbol<*>>() + enclosingReceiverReads()
    return blocks.any { block -> block.localUses().first.any { it in captured } || block.capturedReceivers().keys.any { it in captured } }
}

/** A captured parameter or `val`: the local declaration knows it by its type and its stable creation facts. */
val FirVariableSymbol<*>.isImmutableCapture: Boolean
    get() = this is FirValueParameterSymbol || this is FirPropertySymbol && isVal

/**
 * The values that the local function or class [declaration] captures, the variables that stand for its immutable
 * captures inside it, and those that stand for the enclosing receivers it reads.
 */
class CaptureScope(
    val declaration: FirDeclaration,
    val variables: Map<FirVariableSymbol<*>, VariableEmbedding>,
    val receivers: Map<FirBasedSymbol<*>, VariableEmbedding>,
    /** Whether converting the declaration statement records its facts: only in a converter that collects them. */
    val recordsSites: Boolean,
) {
    val captured: List<FirVariableSymbol<*>> = declaration.capturedLocals()

    /** The fact alternatives recorded at each conversion of the declaration statement. */
    val sites = mutableListOf<() -> List<Exp>>()

    fun accesses(program: ProgramConversionContext): Map<FirBasedSymbol<*>, PropertyAccessEmbedding> =
        captured.associate { symbol ->
            symbol to (variables[symbol] ?: DetachedVarAccess(program.embedType(symbol.resolvedReturnType)))
        } + receivers

    /**
     * Declares the immutable captures and receivers, except those in [declared], and assumes their type invariants and
     * the facts of every declaration site.
     */
    fun entry(program: ProgramConversionContext, declared: Set<FirBasedSymbol<*>> = emptySet()): List<ExpEmbedding> = buildList {
        (variables + receivers).filterKeys { it !in declared }.values.forEach { variable ->
            add(Declare(variable, null))
            add(variable.withInvariants(program.typeResolver) {
                proven = true
                access = true
            })
        }
        val facts = facts()
        if (facts.isNotEmpty()) add(InhaleDirect(facts.toConjunction()))
    }

    /** What holds of the immutable captures at one of the declaration sites, as Kotlin Booleans. */
    fun facts(): List<ExpEmbedding> {
        val boolean = buildType { boolean() }
        return factExps().map { ExpWrapper(boolean.injection.toRef(it), boolean) }
    }

    /** What holds of the immutable captures at one of the declaration sites. */
    fun factExps(): List<Exp> = combineCreationAlternatives(sites.map { it() })

    companion object {
        fun of(declaration: FirDeclaration, program: ProgramConversionContext, recordsSites: Boolean): CaptureScope {
            val variables = declaration.capturedLocals().filter { it.isImmutableCapture }.associateWith { symbol ->
                PlaceholderVariableEmbedding(
                    ScopedName(ParameterScope, PretypeName("captured_${symbol.name.asString()}_${symbol.source?.startOffset}")),
                    program.embedType(symbol.resolvedReturnType), isUnique = false,
                )
            }
            val receivers = declaration.capturedReceivers().entries.mapIndexed { index, (symbol, type) ->
                symbol to PlaceholderVariableEmbedding(
                    ScopedName(ParameterScope, PretypeName("captured_this_${index}_${declaration.source?.startOffset}")),
                    program.embedType(type), isUnique = false,
                )
            }.toMap()
            return CaptureScope(declaration, variables, receivers, recordsSites)
        }
    }
}

/**
 * Converts the statement that declares the local function or class [declaration]: records the facts that hold for its
 * immutable captures here and sets the shared-local flag of each `var` it captures from the enclosing body.
 */
fun StmtConversionContext.declareLocal(declaration: FirDeclaration): ExpEmbedding {
    val scope = captureScope(declaration)
    if (scope.recordsSites && scope.variables.isNotEmpty()) {
        val immutable = scope.variables.map { (symbol, variable) -> currentValue(symbol) to variable as ExpEmbedding }
        scope.sites += creationFactContext(immutable, scope.variables.values.mapTo(mutableSetOf()) { it.name })
    }
    val flags = scope.captured.filterIsInstance<FirPropertySymbol>().filter { it.isVar && capturedAccess(it) == null }.map {
        val flag = sharedLocalFlag(it)
            ?: throw SnaktInternalException(declaration.source, "Captured local var '${it.name}' has no shared-local flag")
        Assign(flag, BooleanLit(true))
    }
    return (flags + UnitLit).toBlock()
}

private fun StmtConversionContext.currentValue(symbol: FirVariableSymbol<*>): ExpEmbedding =
    capturedAccess(symbol)?.getValue(this) ?: embedLocalSymbol(symbol)

/** Rejects two declarations that embed to one Viper name when one of them is local. */
class DistinctNames {
    private val names = mutableMapOf<SymbolicName, Pair<FirBasedSymbol<*>, Boolean>>()

    fun require(symbol: FirFunctionSymbol<*>, program: ProgramConversionContext) {
        val local = symbol.callableId?.isLocal == true && symbol.containingClassLookupTag() == null
        require(symbol.embedName(program), symbol, local, symbol.name)
    }

    fun require(name: SymbolicName, symbol: FirBasedSymbol<*>, local: Boolean, spelling: Name) {
        val first = names.getOrPut(name) { symbol to local }
        if (first.first != symbol && (local || first.second)) {
            throw UnsupportedFeatureException(symbol.source, "two declarations named `$spelling` in one program, one of them local")
        }
    }
}

/**
 * The local declaration whose captures the body of [declaration] reads, and the declarations that contain it. A local
 * function is its own scope; a member of a local class reads the captures of the outermost local class.
 */
@OptIn(SymbolInternals::class)
fun localCaptureScopeOf(declaration: FirDeclaration, containing: List<FirBasedSymbol<*>>): Pair<FirDeclaration, List<FirBasedSymbol<*>>>? {
    if (declaration is FirSimpleFunction && declaration.symbol.callableId.isLocal && declaration.symbol.containingClassLookupTag() == null) {
        return declaration to containing
    }
    val chain = containing + declaration.symbol
    val index = chain.indexOfFirst { it is FirRegularClassSymbol && it.classId.isLocal }
    if (index < 0) return null
    return (chain[index] as FirRegularClassSymbol).fir to chain.take(index)
}

/** The capture scopes of one program, and the scope that the body of its registered declaration reads. */
class LocalCaptureScopes(private val program: ProgramConverter, private val collectsFacts: Boolean) {
    private val scopes = mutableMapOf<FirDeclaration, CaptureScope>()
    val names = DistinctNames()

    var bodyScope: CaptureScope? = null
        private set

    fun scope(declaration: FirDeclaration): CaptureScope = scopes.getOrPut(declaration) { CaptureScope.of(declaration, program, collectsFacts) }

    /** Collects the captures that the body of the registered [declaration] reads. */
    fun prepare(declaration: FirDeclaration) {
        bodyScope = enclosing(declaration)
    }

    /** The capture scope whose values the body of [declaration] reads, with the facts it may assume. */
    fun enclosing(declaration: FirDeclaration): CaptureScope? {
        val (local, outer) = localCaptureScopeOf(declaration, program.checkerContext.containingDeclarations) ?: return null
        val scope = scope(local)
        if (scope.variables.isNotEmpty() && scope.sites.isEmpty()) {
            val facts = try {
                scratch.localCaptures.collect(local, outer).factExps()
            } catch (_: UnsupportedFeatureException) {
                // Best-effort: when the enclosing functions do not convert, the declaration gets no facts from them.
                emptyList()
            }
            scope.sites += { facts.filter(program.portableNames()) }
        }
        return scope
    }

    private val scratch by lazy { ProgramConverter(program.session, program.config, program.checkerContext, DiscardingReporter, collectsCaptureFacts = true) }

    @OptIn(SymbolInternals::class)
    private fun collect(declaration: FirDeclaration, containing: List<FirBasedSymbol<*>>): CaptureScope {
        val scope = scope(declaration)
        val owner = (containing.lastOrNull() as? FirNamedFunctionSymbol)?.fir as? FirSimpleFunction
        if (owner == null || with(program) { SpecifiedLambda.collect(owner) }.isNotEmpty()) return scope.apply { sites.clear() }
        if (scope.variables.isEmpty() || scope.sites.isNotEmpty()) return scope
        val ownerScope = localCaptureScopeOf(owner, containing.dropLast(1))?.let { (local, outer) -> collect(local, outer) }
        convertBody(owner, ownerScope)
        return scope
    }

    /** Converts the body of [owner], which reads the captures of [ownerScope], for what its statements record, and discards it. */
    fun convertBody(owner: FirSimpleFunction, ownerScope: CaptureScope?) = with(program) {
        val signature = embedCompleteSignature(owner.symbol)
        val context = createBodyConversionContext(owner.symbol, signature, uniquenessOutcomeOf(owner).analysis, ownerScope)
        if (signature.signature.isPure) context.convertPureBody(owner)
        else context.convertImpureBody(owner, signature.signature, signature.returnTarget, ownerScope?.entry(this).orEmpty())
    }
}

private fun ProgramConverter.portableNames(): (Exp) -> Boolean {
    val domainFunctions = RuntimeTypeDomain(typeResolver).functions.mapTo(mutableSetOf()) { it.name }
    val specialFunctions = (SpecialFunctions.all + SpecialFunctions.multiset.takeIf { typeResolver.usesMultiset }.orEmpty() +
        SpecialFunctions.iterationSequence.takeIf { typeResolver.usesIterationSequence }.orEmpty()).mapTo(mutableSetOf()) { it.name }
    fun Exp.portable(): Boolean = when (this) {
        is Exp.DomainFuncApp -> function.name in domainFunctions && args.all { it.portable() }
        is Exp.FuncApp -> functionName in specialFunctions && args.all { it.portable() }
        is Exp.FieldAccess, is Exp.Unfolding, is Exp.PredicateAccess, is Exp.PredicateInstance, is Exp.LetBinding -> false
        else -> subExps().all { it.portable() }
    }
    return { it.portable() }
}

private object DiscardingReporter : DiagnosticReporter() {
    override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {}
}

/** Throws when code of the locally scoped [this], which is [construct], reads a value it captures. */
fun FirDeclaration.rejectCaptureReads(source: KtSourceElement?, construct: String) {
    if (isLocallyScoped() && (capturedLocals().isNotEmpty() || enclosingReceiverReads().isNotEmpty())) {
        throw UnsupportedFeatureException(source, "$construct that reads a captured variable")
    }
}

/** Throws when the contract of the locally scoped [this] reads a value it captures, at its source when it is [registered]. */
@OptIn(SymbolInternals::class)
fun FirFunctionSymbol<*>.rejectContractCaptureReads(registered: FirBasedSymbol<*>?) {
    if (fir.contractReadsCapture()) {
        throw UnsupportedFeatureException(source.takeIf { this == registered }, "a contract that reads a captured variable")
    }
}

/** Adds the facts of [scope] to the creation-path facts of [this] body context. */
fun StmtConversionContext.withCaptureFacts(scope: CaptureScope?): StmtConversionContext =
    if (scope == null) this else (this as StmtConverter).copy(creationPathFacts = creationPathFacts + scope.facts())
