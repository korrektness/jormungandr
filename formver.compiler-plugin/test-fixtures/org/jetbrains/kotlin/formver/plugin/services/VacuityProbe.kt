/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import org.jetbrains.kotlin.formver.viper.seqOf
import org.jetbrains.kotlin.formver.viper.toScalaSeq
import scala.jdk.javaapi.CollectionConverters
import viper.silver.ast.FalseLit
import viper.silver.ast.Method
import viper.silver.ast.`NoTrafos$`
import viper.silver.ast.Program
import viper.silver.ast.Seqn
import viper.silver.ast.SimpleInfo
import viper.silver.ast.Stmt
import viper.silver.ast.VirtualPosition
import viper.silver.plugin.standard.refute.Refute
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The vacuity check: every method body gets `refute false` at its entry and at its end. A probe that fails shows
 * that the verifier holds contradictory facts there, so every assertion after it verifies vacuously.
 *
 * Enabled by the `formver.vacuityReport` system property, naming the directory that collects, per test file, the
 * probes that failed in declarations whose verification otherwise succeeds.
 */
object VacuityProbe {
    enum class Point { ENTRY, EXIT }

    private const val MARKER = "vacuity-probe"

    /** A probe's comment: [MARKER], its [Point], and its method's name. */
    private const val COMMENT_SIZE = 3

    val report: Path? = System.getProperty("formver.vacuityReport")?.let { Path.of(it) }

    fun probe(program: Program): Program = program.copy(
        program.domains(), program.fields(), program.functions(), program.predicates(),
        CollectionConverters.asJava(program.methods()).map(::probe).toScalaSeq(),
        program.extensions(), program.pos(), program.info(), program.errT(),
    )

    private fun probe(method: Method): Method {
        if (method.body().isEmpty) return method
        val body: Seqn = method.body().get()
        val stmts = listOf(refute(method, Point.ENTRY), body, refute(method, Point.EXIT))
        val probed = Seqn(stmts.toScalaSeq(), seqOf(), body.pos(), body.info(), body.errT())
        return method.copy(
            method.name(), method.formalArgs(), method.formalReturns(), method.pres(), method.posts(),
            scala.Option.apply(probed), method.pos(), method.info(), method.errT(),
        )
    }

    // RefutePlugin matches a refutation to its outcome by position, so each probe needs a position of its own.
    private fun refute(method: Method, point: Point): Stmt {
        val info = SimpleInfo(seqOf(MARKER, point.name, method.name()))
        val pos = VirtualPosition("$MARKER ${point.name} ${method.name()}")
        return Refute(FalseLit(pos, info, `NoTrafos$`.`MODULE$`), pos, info, `NoTrafos$`.`MODULE$`)
    }

    data class Hit(val method: String, val point: Point)

    /** The probe that [error] reports as failed, or null when [error] is not a probe's. */
    fun hitOf(error: VerifierError): Hit? {
        val node = (error as? VerificationError)?.result?.offendingNode() as? Refute ?: return null
        val comment = (node.info() as? SimpleInfo)?.comment()?.let { CollectionConverters.asJava(it) } ?: return null
        if (comment.size != COMMENT_SIZE || comment[0] != MARKER) return null
        return Hit(comment[2], Point.valueOf(comment[1]))
    }

    /** Appends [hits] to the test file's report, and saves the probed [programs] beside it for rerunning in Viper. */
    @Synchronized
    fun record(directory: Path, testFile: String, declaration: String, hits: List<Hit>, programs: List<Program>) {
        val lines = hits.joinToString("") { "$testFile\t$declaration\t${it.method}\t${it.point}\n" }
        Files.createDirectories(directory)
        val stem = testFile.replace('/', '_')
        Files.writeString(directory.resolve("$stem.tsv"), lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        programs.forEachIndexed { index, program ->
            Files.writeString(directory.resolve("$stem.${hits.first().method}.$index.vpr"), program.toString())
        }
    }
}
