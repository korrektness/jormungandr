package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.embeddings.ModelOrigin
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.seqOf
import scala.jdk.javaapi.CollectionConverters
import viper.silicon.interfaces.SiliconMappedCounterexample
import viper.silicon.reporting.*
import viper.silicon.state.terms.Sort
import viper.silicon.state.terms.Term
import viper.silver.ast.*
import viper.silver.verifier.ValueEntry
import java.math.BigInteger

private const val ENTRY_STATE = "old"

private fun Node.origin(): ModelOrigin? = when (val info = (Info.fromSilver(prettyMetadata._2()) as? Info.Wrapped)?.info) {
    is ModelOrigin -> info
    is SourceRole.FirSymbolHolder -> info.modelOrigin
    else -> null
}

private fun Node.descendants(): List<Node> {
    val iterator = iterator()
    return buildList { while (iterator.hasNext()) add(iterator.next()) }
}

/** Silicon reports values it cannot evaluate as [OtherEntry]; they identify no Kotlin value. */
private fun ExtractedModelEntry.knownValue(): ValueEntry? = if (this is OtherEntry) null else asValueEntry()

private fun variableName(node: Node): String? = when (node) {
    is LocalVar -> node.name()
    is LocalVarDecl -> node.name()
    else -> null
}

private fun variableOrigins(nodes: List<Node>): Map<String, ModelOrigin> {
    // Source attribution is best-effort: ambiguous names must not borrow another declaration's provenance.
    val attributed = nodes.mapNotNull { node -> variableName(node)?.let { name -> node.origin()?.let { name to it } } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (name, origins) -> origins.distinct().singleOrNull()?.let { name to it } }
        .toMap()
    val pathCounts = attributed.values.groupingBy { it.name }.eachCount()
    return attributed.filterValues { pathCounts[it.name] == 1 }
}

private fun VerificationError.siliconModel(): SiliconMappedCounterexample? =
    CollectionConverters.asJava(result.failureContexts()).firstNotNullOfOrNull { context ->
        context.counterExample().takeIf { it.isDefined }?.get() as? SiliconMappedCounterexample
    }

private fun VerificationError.failingMember(nodes: List<Node>): Node? =
    result.scope().takeIf { it.isDefined }?.get()
        ?: nodes.filterIsInstance<Member>().singleOrNull { member -> member.descendants().any { it == result.offendingNode() } }

/**
 * Only source-attributed values supported by the Kotlin presentation are exposed. An [arithmetic] failure's model is
 * checked against the failing bound or divisor: a model in which the check holds is dropped.
 */
fun VerificationError.kotlinCounterexample(arithmetic: Boolean): Counterexample? {
    val model = siliconModel() ?: return null
    val unchecked = when (if (arithmetic) failingCheckHolds(model) else false) {
        true -> return null
        false -> false
        null -> true
    }
    val nodes = model.program().descendants()
    val scope = failingMember(nodes)?.descendants() ?: nodes
    val exit = result is viper.silver.verifier.errors.PostconditionViolated
    return KotlinCounterexampleMapper(model, nodes, variableOrigins(scope), exit).extract()?.copy(unchecked = unchecked)
}

/** Evaluates the failing conjunct, which Silicon reports with a function application's arguments substituted. */
private fun VerificationError.failingCheckHolds(model: SiliconMappedCounterexample): Boolean? {
    val check = result.reason().offendingNode() as? Exp ?: return null
    val store = CollectionConverters.asJava(model.converter().extractedModel().entries())
        .mapNotNull { (name, entry) -> ModelEvaluator.value(entry)?.let { name to it } }.toMap()
    return (ModelEvaluator(model.nativeModel(), store, model.program()).evaluate(check) as? ModelValue.Bool)?.value
}

private data class SnapshotOrigin(val receiver: String, val path: String, val label: String?, val seen: Set<String>, val order: Int)

/** Bindings list in declaration order; [exit] says whether the failure is at function exit, where `result` is bound. */
private class KotlinCounterexampleMapper(
    private val model: SiliconMappedCounterexample,
    nodes: List<Node>,
    private val variables: Map<String, ModelOrigin>,
    private val exit: Boolean,
) {
    private val native = model.nativeModel()
    private val nativeEntries = CollectionConverters.asJava(native.entries())
    private val converter = model.converter()
    private val fields = nodes.filterIsInstance<Field>().mapNotNull { field -> field.origin()?.let { field.name() to it } }.toMap()
    private val predicates = CollectionConverters.asJava(model.program().predicates()).associateBy { it.name() }
    private val enumValues = enumValues(nodes)
    private val bindings = mutableListOf<Pair<Int, CounterexampleBinding>>()

    fun extract(): Counterexample? {
        collect(converter.extractedModel(), null)
        folded(model.heap(), null)
        CollectionConverters.asJava(converter.modelAtLabel())[ENTRY_STATE]?.let { collect(it, ENTRY_STATE) }
        CollectionConverters.asJava(model.oldHeaps())[ENTRY_STATE]?.let { folded(it.values(), ENTRY_STATE) }
        // A model may contain only unsupported values; an empty presentation carries no Kotlin evidence.
        return bindings.takeIf { it.isNotEmpty() }?.let { found ->
            Counterexample(found.distinctBy { it.second }
                .sortedWith(compareBy({ it.second.oldState != null }, { it.first }, { it.second.path }))
                .map { it.second })
        }
    }

    private fun enumReference(call: FuncApp): Pair<String, String>? {
        val role = (Info.fromSilver(call.prettyMetadata._2()) as? Info.Wrapped)?.info as? SourceRole.FirSymbolHolder
        val symbol = role?.firSymbol as? FirEnumEntrySymbol ?: return null
        return call.funcname() to "${symbol.callableId.className}.${symbol.name}"
    }

    private fun functionValue(function: String): ValueEntry? = when (val entry = nativeEntries[function]) {
        // Pure functions have a snapshot argument in Silicon's model.
        is viper.silver.verifier.MapEntry -> Converter.getFunctionValue(
            native, function, seqOf(viper.silver.verifier.ConstantEntry(Converter.snapUnitId())),
            viper.silicon.state.terms.sorts.`Ref$`.`MODULE$`,
        ).knownValue()
        is ValueEntry -> entry
        else -> null
    }

    private fun enumValues(nodes: List<Node>): Map<ValueEntry, List<String>> =
        nodes.filterIsInstance<FuncApp>().mapNotNull(::enumReference).distinct()
            .mapNotNull { (function, name) -> functionValue(function)?.let { it to name } }
            .groupBy({ it.first }, { it.second })

    private fun isKotlinNull(value: ValueEntry, origin: ModelOrigin): Boolean {
        val kotlinNull = origin.resolvedNull?.let { nativeEntries["$it<Ref>"] } ?: return false
        return value == kotlinNull
    }

    private fun unboxed(value: ValueEntry, origin: ModelOrigin): ExtractedModelEntry? {
        val function = origin.resolvedDecoder ?: return null
        val boolean = origin.kind == ModelOrigin.Kind.BOOLEAN
        val sort: Sort = if (boolean) viper.silicon.state.terms.sorts.`Bool$`.`MODULE$` else viper.silicon.state.terms.sorts.`Int$`.`MODULE$`
        val resultType = if (boolean) "Bool" else "Int"
        return Converter.getFunctionValue(native, "$function<$resultType>", seqOf(value), sort)
    }

    private fun character(entry: ExtractedModelEntry): CounterexampleValue? {
        val code = (entry as? LitIntEntry)?.value()?.bigInteger() ?: return null
        val inRange = code.signum() >= 0 && code <= BigInteger.valueOf(Char.MAX_VALUE.code.toLong())
        return if (inRange) CounterexampleValue.Character(code.toInt().toChar()) else null
    }

    private fun decode(entry: ExtractedModelEntry, origin: ModelOrigin): CounterexampleValue? {
        val value = entry.knownValue() ?: return null
        if (isKotlinNull(value, origin)) return CounterexampleValue.Null
        val decoded = unboxed(value, origin) ?: entry
        return when (origin.kind) {
            ModelOrigin.Kind.INT -> (decoded as? LitIntEntry)?.let { CounterexampleValue.Integer(it.value().bigInteger()) }
            ModelOrigin.Kind.BOOLEAN -> (decoded as? LitBoolEntry)?.let { CounterexampleValue.BooleanValue(it.value()) }
            ModelOrigin.Kind.CHAR -> character(decoded)
            ModelOrigin.Kind.REFERENCE -> enumValues[value]?.let { CounterexampleValue.EnumLike(it.sorted()) }
        }
    }

    private fun arraySize(entry: ExtractedModelEntry, origin: ModelOrigin): CounterexampleValue? {
        val function = origin.resolvedArraySize ?: return null
        val array = entry.knownValue() ?: return null
        val snapshot = viper.silver.verifier.ConstantEntry(Converter.snapUnitId())
        val size = Converter.getFunctionValue(native, function, seqOf(snapshot, array), viper.silicon.state.terms.sorts.`Int$`.`MODULE$`)
        return (size as? LitIntEntry)?.let { CounterexampleValue.Integer(it.value().bigInteger()) }
    }

    /**
     * A parameter's own value cannot change, so its value in an old state is omitted; values reached through it are
     * kept. Read-only fields, such as a collection's size, are shown through any reference, and only in the failing
     * state.
     */
    private fun add(
        path: String, entry: ExtractedModelEntry, origin: ModelOrigin, label: String?, seen: Set<String>, order: Int,
        root: Boolean = false,
    ) {
        if (!root || label == null) {
            decode(entry, origin)?.let { bindings.add(order to CounterexampleBinding(path, it, origin.source, label)) }
        }
        if (label == null) arraySize(entry, origin)?.let { bindings.add(order to CounterexampleBinding("$path.size", it)) }
        if (entry !is RefEntry || entry.name() in seen) return
        CollectionConverters.asJava(entry.fields()).forEach { (field, pair) ->
            val fieldOrigin = fields[field] ?: return@forEach
            val shown = if (fieldOrigin.readOnly) label == null else origin.unique
            if (shown) add("$path.${fieldOrigin.name}", pair._1(), fieldOrigin, label, seen + entry.name(), order)
        }
    }

    private fun tracked(name: String, label: String?): ModelOrigin? =
        variables[name]?.takeIf { (label == null || it.parameter) && (exit || !it.result) }

    /** The returned value follows the declared variables; `this`, without a declaration, precedes them. */
    private fun order(origin: ModelOrigin): Int =
        origin.source?.startOffset ?: if (origin.result) Int.MAX_VALUE else -1

    private fun collect(extracted: ExtractedModel, label: String?) {
        CollectionConverters.asJava(extracted.entries()).forEach { (name, entry) ->
            tracked(name, label)?.let { add(it.name, entry, it, label, emptySet(), order(it), root = true) }
        }
    }

    private fun uniqueRoots(label: String?): Map<ValueEntry, List<ModelOrigin>> =
        CollectionConverters.asJava(converter.extractedModel().entries()).mapNotNull { (name, entry) ->
            val origin = tracked(name, label)?.takeIf { it.unique } ?: return@mapNotNull null
            entry.knownValue()?.let { it to origin }
        }.groupBy({ it.first }, { it.second })

    private fun folded(heap: scala.collection.Iterable<viper.silicon.interfaces.state.Chunk>, label: String?) {
        val roots = uniqueRoots(label)
        val chunks = heap.iterator()
        while (chunks.hasNext()) {
            val chunk = chunks.next() as? viper.silicon.state.BasicChunk ?: continue
            if (chunk.args().size() != 1) continue
            val receiver = Converter.evaluateTerm(chunk.args().apply(0), native).knownValue() ?: continue
            roots[receiver]?.forEach { predicate(chunk.id().name(), chunk.snap(), it.name, label, emptySet(), order(it)) }
        }
    }

    private fun predicate(name: String, snapshot: Term, path: String, label: String?, seen: Set<String>, order: Int) {
        val definition = predicates[name] ?: return
        if (name in seen || definition.body().isEmpty || definition.formalArgs().size() != 1) return
        val origin = SnapshotOrigin(definition.formalArgs().apply(0).name(), path, label, seen + name, order)
        walk(definition.body().get(), snapshot, origin)
    }

    private fun walk(expression: Exp, snapshot: Term, origin: SnapshotOrigin) {
        when (expression) {
            is And -> conjunction(expression, snapshot, origin)
            is FieldAccessPredicate -> field(expression, snapshot, origin)
            is PredicateAccessPredicate -> nested(expression, snapshot, origin)
            // Conditional and quantified snapshots require branch/instance evidence; omit them rather than guess.
            else -> Unit
        }
    }

    private fun nested(expression: PredicateAccessPredicate, snapshot: Term, origin: SnapshotOrigin) {
        val access = expression.loc()
        if (access.args().size() == 1 && (access.args().apply(0) as? LocalVar)?.name() == origin.receiver)
            predicate(access.predicateName(), snapshot, origin.path, origin.label, origin.seen, origin.order)
    }

    private fun conjunction(expression: And, snapshot: Term, origin: SnapshotOrigin) {
        val parts = conjuncts(expression)
        var rest = snapshot
        parts.forEachIndexed { index, part ->
            walk(part, if (index == parts.lastIndex) rest else viper.silicon.state.terms.First.apply(rest), origin)
            if (index != parts.lastIndex) rest = viper.silicon.state.terms.Second.apply(rest)
        }
    }

    private fun field(expression: FieldAccessPredicate, snapshot: Term, origin: SnapshotOrigin) {
        val access = expression.loc()
        if ((access.rcv() as? LocalVar)?.name() != origin.receiver) return
        val fieldOrigin = fields[access.field().name()] ?: return
        val sort = Converter.typeToSort(access.typ())
        if (sort.isEmpty) return
        val value = Converter.evaluateTerm(snapshot.convert(sort.get()), native)
        if (fieldOrigin.readOnly && origin.label != null) return
        add("${origin.path}.${fieldOrigin.name}", value, fieldOrigin, origin.label, emptySet(), origin.order)
    }
}

private fun conjuncts(expression: Exp): List<Exp> =
    if (expression is And) conjuncts(expression.left()) + conjuncts(expression.right()) else listOf(expression)
