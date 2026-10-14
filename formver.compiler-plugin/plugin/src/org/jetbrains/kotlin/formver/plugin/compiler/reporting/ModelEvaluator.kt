package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.formver.viper.toScalaSeq
import scala.jdk.javaapi.CollectionConverters
import viper.silicon.reporting.*
import viper.silicon.state.terms.Sort
import viper.silver.ast.*
import viper.silver.verifier.ConstantEntry
import viper.silver.verifier.Model
import viper.silver.verifier.ValueEntry
import java.math.BigInteger

/** A Viper value in a solver model: a mathematical integer or boolean, an opaque model element, or an injected value. */
sealed interface ModelValue {
    data class Integer(val value: BigInteger) : ModelValue
    data class Bool(val value: Boolean) : ModelValue
    data class Element(val entry: ValueEntry) : ModelValue
    /**
     * [value] injected into references by [injection]. It stays symbolic: the model need not list the injection at
     * arguments the solver never saw, and its fallback value there is not the injected one.
     */
    data class Injected(val injection: String, val value: ModelValue) : ModelValue
}

/**
 * Evaluates heap-independent Viper expressions in a solver model. A result of null means the expression has no value
 * the model determines: it reads the heap, an unknown variable, or a function value the model does not give.
 */
class ModelEvaluator(
    private val native: Model,
    private val store: Map<String, ModelValue>,
    program: Program,
) {
    private val functions = CollectionConverters.asJava(program.functions()).associateBy { it.name() }

    /** Maps `fromRef` to `toRef` where the program states `fromRef(toRef(v)) == v` for every `v`. */
    private val injections: Map<String, String> = CollectionConverters.asJava(program.domains())
        .flatMap { CollectionConverters.asJava(it.axioms()) }
        .mapNotNull { injection(it.exp()) }
        .toMap()

    fun evaluate(exp: Exp): ModelValue? = evaluate(exp, emptyMap())

    /** [env] gives the values of a function's formals while evaluating its body. */
    private fun evaluate(exp: Exp, env: Map<String, ModelValue>): ModelValue? = when (exp) {
        is IntLit -> ModelValue.Integer(exp.i().bigInteger())
        is BoolLit -> ModelValue.Bool(exp.value())
        is LocalVar -> env[exp.name()] ?: store[exp.name()]
        is Minus -> integer(exp.exp(), env)?.let { ModelValue.Integer(it.negate()) }
        is Add -> arithmetic(exp.left(), exp.right(), env) { a, b -> a + b }
        is Sub -> arithmetic(exp.left(), exp.right(), env) { a, b -> a - b }
        is Mul -> arithmetic(exp.left(), exp.right(), env) { a, b -> a * b }
        is Div -> arithmetic(exp.left(), exp.right(), env) { a, b -> euclidean(a, b)?.first }
        is Mod -> arithmetic(exp.left(), exp.right(), env) { a, b -> euclidean(a, b)?.second }
        is LtCmp -> comparison(exp.left(), exp.right(), env) { it < 0 }
        is LeCmp -> comparison(exp.left(), exp.right(), env) { it <= 0 }
        is GtCmp -> comparison(exp.left(), exp.right(), env) { it > 0 }
        is GeCmp -> comparison(exp.left(), exp.right(), env) { it >= 0 }
        is EqCmp -> equality(exp.left(), exp.right(), env)
        is NeCmp -> equality(exp.left(), exp.right(), env)?.let { ModelValue.Bool(!it.value) }
        is Not -> boolean(exp.exp(), env)?.let { ModelValue.Bool(!it) }
        is And -> logical(exp.left(), exp.right(), env) { a, b -> a && b }
        is Or -> logical(exp.left(), exp.right(), env) { a, b -> a || b }
        is Implies -> logical(exp.left(), exp.right(), env) { a, b -> !a || b }
        is CondExp -> boolean(exp.cond(), env)?.let { evaluate(if (it) exp.thn() else exp.els(), env) }
        is DomainFuncApp -> domainApplication(exp, env)
        is FuncApp -> functionApplication(exp, env)
        else -> null
    }

    private fun integer(exp: Exp, env: Map<String, ModelValue>): BigInteger? =
        (evaluate(exp, env) as? ModelValue.Integer)?.value

    private fun boolean(exp: Exp, env: Map<String, ModelValue>): Boolean? =
        (evaluate(exp, env) as? ModelValue.Bool)?.value

    private fun arithmetic(
        left: Exp, right: Exp, env: Map<String, ModelValue>, operation: (BigInteger, BigInteger) -> BigInteger?,
    ): ModelValue? {
        val a = integer(left, env) ?: return null
        val b = integer(right, env) ?: return null
        return operation(a, b)?.let { ModelValue.Integer(it) }
    }

    /** Viper integer division and modulo follow SMT-LIB: the remainder is never negative. */
    private fun euclidean(a: BigInteger, b: BigInteger): Pair<BigInteger, BigInteger>? {
        if (b.signum() == 0) return null
        val remainder = a.mod(b.abs())
        return (a - remainder).divide(b) to remainder
    }

    private fun comparison(left: Exp, right: Exp, env: Map<String, ModelValue>, holds: (Int) -> Boolean): ModelValue? {
        val a = integer(left, env) ?: return null
        val b = integer(right, env) ?: return null
        return ModelValue.Bool(holds(a.compareTo(b)))
    }

    private fun equality(left: Exp, right: Exp, env: Map<String, ModelValue>): ModelValue.Bool? {
        val a = evaluate(left, env) ?: return null
        val b = evaluate(right, env) ?: return null
        return equal(a, b)?.let { ModelValue.Bool(it) }
    }

    /** Null where the model does not decide equality, as between an injected value and a model element. */
    private fun equal(a: ModelValue, b: ModelValue): Boolean? = when {
        // An injection is injective, since `fromRef` inverts it.
        a is ModelValue.Injected && b is ModelValue.Injected -> if (a.injection == b.injection) equal(a.value, b.value) else null
        // Distinct elements of a model's universe denote distinct values.
        a !is ModelValue.Injected && a::class == b::class -> a == b
        else -> null
    }

    private fun logical(
        left: Exp, right: Exp, env: Map<String, ModelValue>, operation: (Boolean, Boolean) -> Boolean,
    ): ModelValue? {
        val a = boolean(left, env) ?: return null
        val b = boolean(right, env) ?: return null
        return ModelValue.Bool(operation(a, b))
    }

    private fun domainApplication(exp: DomainFuncApp, env: Map<String, ModelValue>): ModelValue? {
        val values = CollectionConverters.asJava(exp.args()).map { evaluate(it, env) ?: return null }
        val argument = values.singleOrNull()
        if (argument != null && exp.funcname() in injections.values) return ModelValue.Injected(exp.funcname(), argument)
        if (argument is ModelValue.Injected) {
            return argument.value.takeIf { injections[exp.funcname()] == argument.injection }
        }
        return lookup("${exp.funcname()}<${sortName(exp.typ()) ?: return null}>", values, exp.typ())
    }

    private fun functionApplication(exp: FuncApp, env: Map<String, ModelValue>): ModelValue? {
        val function = functions[exp.funcname()] ?: return null
        val values = CollectionConverters.asJava(exp.args()).map { evaluate(it, env) ?: return null }
        val formals = CollectionConverters.asJava(function.formalArgs()).map { it.name() }.zip(values).toMap()
        val pres = CollectionConverters.asJava(function.pres())
        // Silicon establishes an application's precondition before any check that encloses it, so only a precondition
        // these values falsify leaves the application undefined.
        if (pres.any { evaluate(it, formals) == ModelValue.Bool(false) }) return null
        val body = function.body()
        if (body.isDefined) return evaluate(body.get(), formals)
        definition(function)?.let { (injection, value) -> return evaluate(value, formals)?.let { ModelValue.Injected(injection, it) } }
        // A function without a body or precondition is a model function of the unit snapshot and its arguments.
        if (pres.isNotEmpty()) return null
        val snapshot = ModelValue.Element(ConstantEntry(Converter.snapUnitId()))
        return lookup(exp.funcname(), listOf(snapshot) + values, exp.typ())
    }

    /** A postcondition `fromRef(result) == value` defines the result as `value` injected by the matching `toRef`. */
    private fun definition(function: viper.silver.ast.Function): Pair<String, Exp>? =
        CollectionConverters.asJava(function.posts()).firstNotNullOfOrNull { post ->
            val equality = post as? EqCmp ?: return@firstNotNullOfOrNull null
            val decoded = equality.left() as? DomainFuncApp ?: return@firstNotNullOfOrNull null
            val injection = injections[decoded.funcname()] ?: return@firstNotNullOfOrNull null
            val argument = CollectionConverters.asJava(decoded.args()).singleOrNull()
            if (argument !is Result || mentionsResult(equality.right())) null else injection to equality.right()
        }

    private fun mentionsResult(exp: Exp): Boolean {
        val nodes = exp.iterator()
        while (nodes.hasNext()) if (nodes.next() is Result) return true
        return false
    }

    private fun lookup(name: String, args: List<ModelValue>, type: Type): ModelValue? {
        val sort = sort(type) ?: return null
        val entries = args.map { entry(it) ?: return null }
        return value(Converter.getFunctionValue(native, name, entries.toScalaSeq(), sort))
    }

    private fun entry(value: ModelValue): ValueEntry? = when (value) {
        is ModelValue.Integer -> LitIntEntry(scala.math.BigInt(value.value)).asValueEntry()
        is ModelValue.Bool -> LitBoolEntry(value.value).asValueEntry()
        is ModelValue.Element -> value.entry
        is ModelValue.Injected -> null
    }

    private fun sort(type: Type): Sort? = Converter.typeToSort(type).let { if (it.isDefined) it.get() else null }

    private fun sortName(type: Type): String? = type.toString().takeIf { it in setOf("Int", "Bool", "Ref") }

    private fun injection(axiom: Exp): Pair<String, String>? {
        val forall = axiom as? Forall ?: return null
        val variable = CollectionConverters.asJava(forall.variables()).singleOrNull()?.name() ?: return null
        val equality = forall.exp() as? EqCmp ?: return null
        if ((equality.right() as? LocalVar)?.name() != variable) return null
        val outer = equality.left() as? DomainFuncApp ?: return null
        val inner = CollectionConverters.asJava(outer.args()).singleOrNull() as? DomainFuncApp ?: return null
        val argument = CollectionConverters.asJava(inner.args()).singleOrNull() as? LocalVar ?: return null
        return if (argument.name() == variable) outer.funcname() to inner.funcname() else null
    }

    companion object {
        fun value(entry: ExtractedModelEntry): ModelValue? = when (entry) {
            is LitIntEntry -> ModelValue.Integer(entry.value().bigInteger())
            is LitBoolEntry -> ModelValue.Bool(entry.value())
            is OtherEntry -> null
            else -> ModelValue.Element(entry.asValueEntry())
        }
    }
}
