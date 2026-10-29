package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.embeddings.ModelOrigin
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.toFailure
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.*
import scala.Option
import scala.Tuple2
import viper.silicon.interfaces.SiliconFailureContext
import viper.silicon.interfaces.SiliconMappedCounterexample
import viper.silicon.state.*
import viper.silicon.state.terms.IntLiteral
import viper.silicon.state.terms.Term
import viper.silver.ast.*
import viper.silver.verifier.Model
import viper.silver.verifier.errors
import viper.silver.verifier.reasons
import kotlin.test.*

class CounterexampleReportingTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`
    private val int = `Int$`.`MODULE$`
    private val parameterSource = diagnosticSource(10)
    private val localSource = diagnosticSource(20)

    private fun backend(ambiguous: Boolean = false, unsupported: Boolean = false): VerificationError {
        fun declaration(name: String, origin: ModelOrigin) = LocalVarDecl(name, int, pos, with(org.jetbrains.kotlin.formver.core.names.SimpleNameResolver()) { Info.Wrapped(origin).toSilver() }, trafos)
        val parameter = declaration("x", ModelOrigin("argument", parameterSource, ModelOrigin.Kind.INT, parameter = true))
        val local = declaration("local", ModelOrigin("local", localSource, ModelOrigin.Kind.INT))
        val invalidChar = declaration("invalidChar", ModelOrigin("invalidChar", null, ModelOrigin.Kind.CHAR))
        val unknown = declaration("unknown", ModelOrigin("unknown", null, ModelOrigin.Kind.REFERENCE))
        val ghost = LocalVarDecl("temporary", int, pos, info, trafos)
        val condition = FalseLit(pos, info, trafos)
        val assertion = Assert(condition, pos, info, trafos)
        val declarations = if (unsupported) listOf(invalidChar, unknown, ghost) else listOf(parameter, local, invalidChar, unknown, ghost)
        val body = Seqn(seqOf<Stmt>(assertion), declarations.toScalaSeq(), pos, info, trafos)
        val method = Method("m", emptySeq(), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        val methods = if (ambiguous) {
            val other = declaration("x", ModelOrigin("other", diagnosticSource(30), ModelOrigin.Kind.INT, parameter = true))
            listOf(method, Method("other", seqOf(other), emptySeq(), emptySeq(), emptySeq(), Option.empty(), pos, info, trafos))
        } else listOf(method)
        val program = Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), methods.toScalaSeq(), emptySeq(), pos, info, trafos)
        var values = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<AbstractLocalVar, Tuple2<Term, Option<Exp>>>()
        declarations.forEach { decl ->
            val value = when (decl.name()) { "x" -> 7; "local" -> 8; "invalidChar" -> 70000; else -> 0 }
            values = values.updated(LocalVar(decl.name(), decl.typ(), pos, decl.info(), trafos), Tuple2<Term, Option<Exp>>(IntLiteral(value.toScalaBigInt()), Option.empty()))
        }
        val heap = ListBackedHeap(scala.collection.immutable.`Vector$`.`MODULE$`.empty())
        val old = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<String, Heap>().updated<Heap>("old", heap)
        val model = SiliconMappedCounterexample(MapBackedStore(values), heap.values(), old, Model.apply(""), program)
        val result = errors.AssertFailed(assertion, reasons.AssertionFalse(condition), false)
        result.`failureContexts_$eq`(seqOf(SiliconFailureContext(emptySeq(), model.toScalaOption(), Option.empty())))
        return VerificationError(result)
    }

    @Test
    fun `mapped values retain Kotlin bindings and related declarations without old parameter values`() {
        val failure = backend().toFailure(diagnosticSource(40))
        val bindings = assertNotNull(failure.counterexample).bindings
        assertEquals(listOf("argument", "local"), bindings.map { it.path })
        assertEquals(listOf(null, null), bindings.map { it.oldState })
        assertEquals(listOf(7, 8).map { CounterexampleValue.Integer(it.toBigInteger()) }, bindings.map { it.value })
        assertEquals(listOf(parameterSource, localSource), bindings.map { it.source })
        assertEquals(listOf(parameterSource, localSource), failure.related.map { it.source })
        assertEquals(FailureKind.VIPER_VERIFICATION_ERROR, failure.kind)
        assertNotNull(failure.backend)
    }

    @Test
    fun `variables in another member do not replace the failing scopes provenance`() {
        val failure = backend(ambiguous = true).toFailure(null)
        assertEquals(backend().toFailure(null).counterexample, failure.counterexample)
        assertEquals(listOf(parameterSource, localSource), failure.related.map { it.source })
    }

    private fun fieldBackend(unique: Boolean): VerificationError {
        val resolver = org.jetbrains.kotlin.formver.core.names.SimpleNameResolver()
        val ref = `Ref$`.`MODULE$`
        val origin = ModelOrigin("box", parameterSource, ModelOrigin.Kind.REFERENCE, unique = unique, parameter = true)
        val fieldOrigin = ModelOrigin("value", localSource, ModelOrigin.Kind.INT)
        val declaration = LocalVarDecl("box", ref, pos, with(resolver) { Info.Wrapped(origin).toSilver() }, trafos)
        val field = Field("field", int, pos, with(resolver) { Info.Wrapped(fieldOrigin).toSilver() }, trafos)
        val condition = FalseLit(pos, info, trafos)
        val assertion = Assert(condition, pos, info, trafos)
        val body = Seqn(seqOf<Stmt>(assertion), emptySeq(), pos, info, trafos)
        val method = Method("m", seqOf(declaration), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        val program = Program(emptySeq(), seqOf(field), emptySeq(), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
        val root = viper.silicon.state.terms.Var(SimpleIdentifier("root"), viper.silicon.state.terms.sorts.`Ref$`.`MODULE$`, false)
        val values = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<AbstractLocalVar, Tuple2<Term, Option<Exp>>>()
            .updated(LocalVar("box", ref, pos, info, trafos), Tuple2<Term, Option<Exp>>(root, Option.empty()))
        fun heap(value: Int): Heap {
            val chunk = BasicChunk(viper.silicon.resources.`FieldID$`.`MODULE$`, BasicChunkIdentifier("field"), seqOf<Term>(root),
                Option.empty(), IntLiteral(value.toScalaBigInt()), Option.empty(),
                viper.silicon.state.terms.`FullPerm$`.`MODULE$`, Option.empty())
            return ListBackedHeap(seqOf<viper.silicon.interfaces.state.Chunk>(chunk).toVector())
        }
        val old = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<String, Heap>().updated<Heap>("old", heap(3))
        val native = Model.apply("root -> Ref!val!1\n\$Ref.null -> Ref!val!0")
        val model = SiliconMappedCounterexample(MapBackedStore(values), heap(4).values(), old, native, program)
        val result = errors.AssertFailed(assertion, reasons.AssertionFalse(condition), false)
        result.`failureContexts_$eq`(seqOf(SiliconFailureContext(emptySeq(), model.toScalaOption(), Option.empty())))
        return VerificationError(result)
    }

    @Test
    fun `unique receiver fields retain current and entry values with field provenance`() {
        val failure = fieldBackend(unique = true).toFailure(diagnosticSource(40))
        val bindings = assertNotNull(failure.counterexample).bindings
        assertEquals(listOf("box.value", "box.value"), bindings.map { it.path })
        assertEquals(listOf(null, "old"), bindings.map { it.oldState })
        assertEquals(listOf(4, 3).map { CounterexampleValue.Integer(it.toBigInteger()) }, bindings.map { it.value })
        assertEquals(listOf(localSource, localSource), bindings.map { it.source })
        assertEquals(listOf(localSource), failure.related.map { it.source })
    }

    @Test
    fun `shared receiver fields are omitted even when Silicon supplies heap values`() {
        assertNull(fieldBackend(unique = false).toFailure(null).counterexample)
    }

    @Test
    fun `references without model values are not reported as null`() {
        val ref = `Ref$`.`MODULE$`
        val origin = ModelOrigin("reference", parameterSource, ModelOrigin.Kind.REFERENCE, parameter = true, resolvedNull = "missingNull")
        val declaration = LocalVarDecl("reference", ref, pos, with(org.jetbrains.kotlin.formver.core.names.SimpleNameResolver()) { Info.Wrapped(origin).toSilver() }, trafos)
        val condition = FalseLit(pos, info, trafos)
        val assertion = Assert(condition, pos, info, trafos)
        val body = Seqn(seqOf<Stmt>(assertion), emptySeq(), pos, info, trafos)
        val method = Method("m", seqOf(declaration), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        val program = Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
        val unknown = viper.silicon.state.terms.Var(SimpleIdentifier("unknown"), viper.silicon.state.terms.sorts.`Ref$`.`MODULE$`, false)
        val values = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<AbstractLocalVar, Tuple2<Term, Option<Exp>>>()
            .updated(LocalVar("reference", ref, pos, info, trafos), Tuple2<Term, Option<Exp>>(unknown, Option.empty()))
        val heap = ListBackedHeap(scala.collection.immutable.`Vector$`.`MODULE$`.empty())
        val old = scala.collection.immutable.`TreeSeqMap$`.`MODULE$`.empty<String, Heap>().updated<Heap>("old", heap)
        val model = SiliconMappedCounterexample(MapBackedStore(values), heap.values(), old, Model.apply(""), program)
        val result = errors.AssertFailed(assertion, reasons.AssertionFalse(condition), false)
        result.`failureContexts_$eq`(seqOf(SiliconFailureContext(emptySeq(), model.toScalaOption(), Option.empty())))
        assertNull(VerificationError(result).toFailure(null).counterexample)
    }

    @Test
    fun `unsupported models preserve the proof failure without a counterexample`() {
        val failure = backend(unsupported = true).toFailure(null)
        assertNull(failure.counterexample)
        assertTrue(failure.related.isEmpty())
        assertNotNull(failure.backend)
        assertEquals(FailureKind.VIPER_VERIFICATION_ERROR, failure.kind)
    }
}
