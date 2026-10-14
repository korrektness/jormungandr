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
