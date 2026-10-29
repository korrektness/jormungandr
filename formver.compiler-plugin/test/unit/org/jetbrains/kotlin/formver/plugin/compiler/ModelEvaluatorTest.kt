package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ModelEvaluator
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ModelValue
import org.jetbrains.kotlin.formver.viper.*
import scala.Option
import viper.silver.ast.*
import viper.silver.verifier.ConstantEntry
import viper.silver.verifier.MapEntry
import viper.silver.verifier.Model
import viper.silver.verifier.ModelEntry
import viper.silver.verifier.ValueEntry
import kotlin.test.*

class ModelEvaluatorTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`
    private val int = `Int$`.`MODULE$`
    private val ref = `Ref$`.`MODULE$`
    private val domain = "Runtime"

    private fun variable(name: String, type: Type) = LocalVar(name, type, pos, info, trafos)
    private fun literal(value: Long) = IntLit(value.toScalaBigInt(), pos, info, trafos)
    private fun toRef(value: Exp) = DomainFuncApp("intToRef", seqOf(value), emptyTypeMap(), pos, info, ref, domain, trafos)
    private fun fromRef(value: Exp) = DomainFuncApp("intFromRef", seqOf(value), emptyTypeMap(), pos, info, int, domain, trafos)
    private fun emptyTypeMap() = scala.collection.immutable.`Map$`.`MODULE$`.empty<TypeVar, Type>()

    /**
     * A runtime domain with an `Int` injection, and the operator function `minusInts`, defined by its postcondition
     * above `Int.MIN_VALUE`.
     */
    private val program: Program = run {
        val v = LocalVarDecl("v", int, pos, info, trafos)
        val functions = seqOf(
            DomainFunc("intToRef", seqOf<AnyLocalVarDecl>(v), ref, false, Option.empty(), pos, info, domain, trafos),
            DomainFunc("intFromRef", seqOf<AnyLocalVarDecl>(LocalVarDecl("r", ref, pos, info, trafos)), int, false, Option.empty(), pos, info, domain, trafos),
        )
        val inverse = Forall(seqOf(v), emptySeq(), EqCmp(fromRef(toRef(variable("v", int))), variable("v", int), pos, info, trafos), pos, info, trafos)
        val runtime = Domain(domain, functions, seqOf<DomainAxiom>(AnonymousDomainAxiom(inverse, pos, info, domain, trafos)), emptySeq(), Option.empty(), pos, info, trafos)
        val difference = Sub(fromRef(variable("a", ref)), fromRef(variable("b", ref)), pos, info, trafos)
        val bound = GeCmp(difference, literal(Int.MIN_VALUE.toLong()), pos, info, trafos)
        val definition = EqCmp(fromRef(Result(ref, pos, info, trafos)), difference, pos, info, trafos)
        val minus = Function("minusInts", seqOf(LocalVarDecl("a", ref, pos, info, trafos), LocalVarDecl("b", ref, pos, info, trafos)),
            ref, seqOf<Exp>(bound), seqOf<Exp>(definition), Option.empty(), pos, info, trafos)
        Program(seqOf(runtime), emptySeq(), seqOf(minus), emptySeq(), emptySeq(), emptySeq(), pos, info, trafos)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V> scalaMap(vararg entries: Pair<K, V>): scala.collection.immutable.Map<K, V> =
        entries.fold(scala.collection.immutable.`Map$`.`MODULE$`.empty<K, V>() as scala.collection.immutable.Map<K, V>) { map, (k, v) ->
            map.updated(k, v) as scala.collection.immutable.Map<K, V>
        }

    /** `intFromRef` maps the model element `Ref!val!1` to [decoded]; other elements decode to 0. */
    private fun evaluator(decoded: Long): ModelEvaluator {
        val options = scalaMap<scala.collection.immutable.Seq<ValueEntry>, ValueEntry>(
            seqOf<ValueEntry>(ConstantEntry("Ref!val!1")) to ConstantEntry(decoded.toString()),
        )
        val entries = scalaMap<String, ModelEntry>("intFromRef<Int>" to MapEntry(options, ConstantEntry("0")))
        return ModelEvaluator(Model.apply(entries), mapOf("i" to ModelValue.Element(ConstantEntry("Ref!val!1"))), program)
    }

    /** The upper bound of `timesInts(i, minusInts(i, intToRef(1)))`, as Silicon reports it with the arguments substituted. */
    private fun upperBound(subtrahend: Long): Exp {
        val difference = FuncApp("minusInts", seqOf(variable("i", ref), toRef(literal(subtrahend))), pos, info, ref, trafos)
        val product = Mul(fromRef(variable("i", ref)), fromRef(difference), pos, info, trafos)
        return LeCmp(product, literal(Int.MAX_VALUE.toLong()), pos, info, trafos)
    }

    @Test
    fun `a bound that holds in the model evaluates to true`() {
        assertEquals(ModelValue.Bool(true), evaluator(172).evaluate(upperBound(1)))
    }

    @Test
    fun `a bound that fails in the model evaluates to false`() {
        assertEquals(ModelValue.Bool(false), evaluator(50000).evaluate(upperBound(1)))
    }

    @Test
    fun `an injected value round-trips without a model lookup`() {
        // The model lists no `intToRef`, so only the injection axiom gives `intFromRef(intToRef(-3))`.
        assertEquals(ModelValue.Integer((-3).toBigInteger()), evaluator(0).evaluate(fromRef(toRef(literal(-3)))))
    }

    @Test
    fun `a result projection does not establish reference identity`() {
        val evaluator = evaluator(7)
        fun difference(a: Long, b: Long) = FuncApp("minusInts", seqOf(toRef(literal(a)), toRef(literal(b))), pos, info, ref, trafos)
        val first = difference(7, 3)
        val second = difference(8, 4)
        assertEquals(ModelValue.Bool(true), evaluator.evaluate(EqCmp(fromRef(first), fromRef(second), pos, info, trafos)))
        // The left inverse axiom permits multiple references with the same decoded integer.
        assertNull(evaluator.evaluate(EqCmp(first, second, pos, info, trafos)))
        assertNull(evaluator.evaluate(EqCmp(first, toRef(literal(4)), pos, info, trafos)))
    }

    private fun recursiveEvaluator(branching: Boolean): ModelEvaluator {
        val n = variable("n", int)
        val next = FuncApp("count", seqOf(Sub(n, literal(1), pos, info, trafos)), pos, info, int, trafos)
        val body = CondExp(
            EqCmp(n, literal(0), pos, info, trafos), literal(1),
            if (branching) Add(next, next, pos, info, trafos) else next, pos, info, trafos,
        )
        val function = Function("count", seqOf(LocalVarDecl("n", int, pos, info, trafos)), int,
            emptySeq(), emptySeq(), Option.apply<Exp>(body), pos, info, trafos)
        val recursiveProgram = Program(emptySeq(), emptySeq(), seqOf(function), emptySeq(), emptySeq(), emptySeq(), pos, info, trafos)
        return ModelEvaluator(Model.apply(scalaMap()), emptyMap(), recursiveProgram)
    }

    private fun count(n: Long) = FuncApp("count", seqOf(literal(n)), pos, info, int, trafos)

    @Test
    fun `recursive evaluation is depth bounded and the budget resets for each check`() {
        val evaluator = recursiveEvaluator(branching = false)
        // Negative arguments recurse indefinitely with distinct argument values.
        assertNull(evaluator.evaluate(count(-1)))
        assertEquals(ModelValue.Integer(1.toBigInteger()), evaluator.evaluate(count(5)))
    }

    @Test
    fun `branching evaluation is work bounded even at shallow depth`() {
        val evaluator = recursiveEvaluator(branching = true)
        assertNull(evaluator.evaluate(count(20)))
        assertEquals(ModelValue.Integer(32.toBigInteger()), evaluator.evaluate(count(5)))
    }

    @Test
    fun `arithmetic growth is bounded independently of expression depth`() {
        val x = variable("x", int)
        val square = Function("square", seqOf(LocalVarDecl("x", int, pos, info, trafos)), int,
            emptySeq(), emptySeq(), Option.apply<Exp>(Mul(x, x, pos, info, trafos)), pos, info, trafos)
        val squareProgram = Program(emptySeq(), emptySeq(), seqOf(square), emptySeq(), emptySeq(), emptySeq(), pos, info, trafos)
        val evaluator = ModelEvaluator(Model.apply(scalaMap()), emptyMap(), squareProgram)
        fun squares(count: Int): Exp = (0 until count).fold(literal(2) as Exp) { value, _ ->
            FuncApp("square", seqOf(value), pos, info, int, trafos)
        }
        assertNull(evaluator.evaluate(squares(20)))
        assertEquals(ModelValue.Integer(4294967296L.toBigInteger()), evaluator.evaluate(squares(5)))
    }

    @Test
    fun `division and modulo leave a nonnegative remainder`() {
        val evaluator = evaluator(0)
        assertEquals(ModelValue.Integer((-4).toBigInteger()), evaluator.evaluate(Div(literal(-7), literal(2), pos, info, trafos)))
        assertEquals(ModelValue.Integer(1.toBigInteger()), evaluator.evaluate(Mod(literal(-7), literal(2), pos, info, trafos)))
        assertEquals(ModelValue.Integer(4.toBigInteger()), evaluator.evaluate(Div(literal(-7), literal(-2), pos, info, trafos)))
        assertNull(evaluator.evaluate(Div(literal(1), literal(0), pos, info, trafos)))
    }

    @Test
    fun `expressions the model does not determine have no value`() {
        val evaluator = evaluator(0)
        assertNull(evaluator.evaluate(fromRef(variable("unknown", ref))))
        assertNull(evaluator.evaluate(EqCmp(toRef(literal(1)), variable("i", ref), pos, info, trafos)))
        // `minusInts` is undefined where its precondition fails.
        assertNull(evaluator.evaluate(upperBound(Long.MAX_VALUE)))
    }
}
