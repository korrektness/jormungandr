/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A trusted piece that a verified result depends on. docs/soundness.md, "The trust report", says what each kind trusts. */
data class TrustedAssumption(val kind: Kind, val name: String) : Comparable<TrustedAssumption> {
    enum class Kind(val label: String) {
        BUNDLED_SPEC("bundled spec"),
        USER_SPEC("user spec"),
        REBINDS_VERIFIED("rebinds verified"),
        NEVER_VERIFY("@NeverVerify"),
        NEVER_CONVERT("@NeverConvert"),
        SKIPPED("skipped"),
        LIBRARY_CONTRACT("library contract"),
    }

    override fun compareTo(other: TrustedAssumption): Int = compareValuesBy(this, other, { it.kind }, { it.name })
}

/** An exact semantic mode selected by a source annotation, independent of trusted assumptions. */
data class SemanticOptIn(val name: String) : Comparable<SemanticOptIn> {
    override fun compareTo(other: SemanticOptIn): Int = name.compareTo(other.name)
}

/**
 * The verified declarations of a module, by source file and then by verification label, each with the trusted
 * assumptions its program used. [render] writes the report file, whose first line is [summary]; [parse] reads it back.
 */
class TrustReport(
    entries: Map<String, Map<String, Set<TrustedAssumption>>>,
    semanticOptIns: Map<String, Map<String, Set<SemanticOptIn>>> = emptyMap(),
) {
    val semanticOptIns: Map<String, Map<String, List<SemanticOptIn>>> =
        semanticOptIns.filterKeys { it in entries }.mapValues { (file, declarations) ->
            declarations.filter { (label, modes) -> label in entries.getValue(file) && modes.isNotEmpty() }
                .mapValues { (_, modes) -> modes.sorted() }
        }.filterValues { it.isNotEmpty() }

    val files: Map<String, Map<String, List<TrustedAssumption>>> =
        entries.filterValues { it.isNotEmpty() }.toSortedMap().mapValues { (_, declarations) ->
            declarations.toSortedMap().mapValues { (_, assumptions) -> assumptions.sorted() }
        }

    val summary: String
        get() {
            val declarations = files.values.flatMap { it.values }
            if (declarations.isEmpty()) return "SnaKt verified no declarations."
            val verified = "SnaKt verified ${declarations.size} declaration${if (declarations.size == 1) "" else "s"}"
            val trusting = declarations.count { it.isNotEmpty() }
            if (trusting == 0) return "$verified; none relies on trusted assumptions."
            val distinct = declarations.flatten().distinct()
            val counts = distinct.groupingBy { it.kind }.eachCount().toSortedMap()
                .entries.joinToString(", ") { (kind, count) -> "${kind.label}: $count" }
            val relies = if (trusting == 1) "relies" else "rely"
            val assumptions = "${distinct.size} distinct trusted assumption${if (distinct.size == 1) "" else "s"}"
            return "$verified; $trusting $relies on $assumptions ($counts)."
        }

    fun render(): String = buildString {
        appendLine(summary)
        for ((file, declarations) in files) {
            appendLine()
            appendLine(file)
            for ((label, assumptions) in declarations) {
                appendLine("$DECLARATION_INDENT$label")
                for (assumption in assumptions) appendLine("$ASSUMPTION_INDENT${assumption.kind.label}: ${assumption.name}")
                for (mode in semanticOptIns[file]?.get(label).orEmpty()) {
                    appendLine("${ASSUMPTION_INDENT}$WRAPPING_LABEL: ${mode.name}")
                }
            }
        }
    }

    /**
     * This report with the files of [newer] replaced by its entries. [recompiled] lists the files that the newer
     * compilation covered, so that a file it no longer verifies anything in loses its old entries. Old entries of a
     * file for which [exists] fails are dropped too.
     */
    fun updatedWith(newer: TrustReport, recompiled: Set<String>, exists: (String) -> Boolean): TrustReport =
        TrustReport(
            entries().filterKeys { it !in recompiled && exists(it) } + newer.entries(),
            modes().filterKeys { it !in recompiled && exists(it) } + newer.modes(),
        )

    /** This report with each file name replaced by [rename] of it. */
    fun renamed(rename: (String) -> String): TrustReport =
        TrustReport(entries().mapKeys { rename(it.key) }, modes().mapKeys { rename(it.key) })

    private fun modes() = semanticOptIns.mapValues { (_, declarations) -> declarations.mapValues { it.value.toSet() } }

    private fun entries() = files.mapValues { (_, declarations) -> declarations.mapValues { it.value.toSet() } }

    companion object {
        private const val DECLARATION_INDENT = "  "
        private const val ASSUMPTION_INDENT = "    "
        private const val WRAPPING_LABEL = "@WrappingIntArithmetic"
        private val kindsByLabel = TrustedAssumption.Kind.entries.associateBy { it.label }

        /** Reads a report that [render] wrote; any other text throws [IllegalArgumentException]. */
        fun parse(text: String): TrustReport {
            val report = parseEntries(text)
            // Comparing the whole text checks the summary line against the entries, and the layout of the entries.
            val rendered = report.render()
            if (text == rendered) return report
            val expected = rendered.split("\n")
            val actual = text.split("\n")
            val differing = (0..maxOf(expected.size, actual.size)).first { expected.getOrNull(it) != actual.getOrNull(it) }
            throw IllegalArgumentException(
                "Malformed trust report line ${differing + 1}: ${actual.getOrNull(differing) ?: "(end of text)"}"
            )
        }

        private fun parseEntries(text: String): TrustReport {
            val entries = linkedMapOf<String, MutableMap<String, MutableSet<TrustedAssumption>>>()
            val modes = linkedMapOf<String, MutableMap<String, MutableSet<SemanticOptIn>>>()
            var file: String? = null
            var declaration: String? = null
            var declarations: MutableMap<String, MutableSet<TrustedAssumption>>? = null
            var assumptions: MutableSet<TrustedAssumption>? = null
            // The first line is the summary, which [parse] checks against the entries.
            for ((index, line) in text.split('\n').withIndex().drop(1)) {
                fun malformed(): Nothing = throw IllegalArgumentException("Malformed trust report line ${index + 1}: $line")
                when {
                    '\r' in line -> malformed()
                    line.isEmpty() -> {}
                    line.startsWith(ASSUMPTION_INDENT) -> {
                        val (label, name) = line.removePrefix(ASSUMPTION_INDENT).split(": ", limit = 2)
                            .takeIf { it.size == 2 } ?: malformed()
                        if (label == WRAPPING_LABEL) {
                            val currentFile = file ?: malformed()
                            val currentDeclaration = declaration ?: malformed()
                            modes.getOrPut(currentFile) { linkedMapOf() }
                                .getOrPut(currentDeclaration) { mutableSetOf() } += SemanticOptIn(name)
                        } else {
                            val kind = kindsByLabel[label] ?: malformed()
                            (assumptions ?: malformed()) += TrustedAssumption(kind, name)
                        }
                    }
                    line.startsWith(DECLARATION_INDENT) -> {
                        val current = declarations ?: malformed()
                        val label = line.removePrefix(DECLARATION_INDENT)
                        if (label in current) malformed()
                        declaration = label
                        assumptions = mutableSetOf<TrustedAssumption>().also { current[label] = it }
                    }
                    line.first().isWhitespace() -> malformed()
                    else -> {
                        if (line in entries) malformed()
                        declarations = linkedMapOf<String, MutableSet<TrustedAssumption>>().also { entries[line] = it }
                        file = line
                        declaration = null
                        assumptions = null
                    }
                }
            }
            return TrustReport(entries, modes)
        }
    }
}

/**
 * Collects the verified declarations of one compilation; checkers on several threads may record into it. [snapshot]
 * evaluates the recorded assumptions, so it must run after every checker.
 */
class TrustLedger {
    private val entries = mutableMapOf<String, MutableMap<String, () -> Set<TrustedAssumption>>>()
    private val semanticOptIns = mutableMapOf<String, MutableMap<String, () -> Set<SemanticOptIn>>>()

    @Synchronized
    fun record(
        file: String,
        label: String,
        modes: () -> Set<SemanticOptIn> = { emptySet() },
        assumptions: () -> Set<TrustedAssumption>,
    ) {
        entries.getOrPut(file) { mutableMapOf() }[label] = assumptions
        semanticOptIns.getOrPut(file) { mutableMapOf() }[label] = modes
    }

    @Synchronized
    fun snapshot(): TrustReport = TrustReport(
        entries.mapValues { (_, labels) -> labels.mapValues { it.value() } },
        semanticOptIns.mapValues { (_, labels) -> labels.mapValues { it.value() } },
    )
}

/**
 * Updates the report at [target] with [report], the declarations a compilation of the source files [recompiled]
 * verified, and returns the result. Both name files by path; the file names in the written report are relative to
 * [target]'s directory, so that the report stays valid when the project moves as a whole. An existing report that
 * [TrustReport.parse] rejects throws [IllegalArgumentException] and stays untouched: starting afresh would silently
 * drop the entries of the files this compilation skipped.
 */
fun writeTrustReport(target: File, report: TrustReport, recompiled: Set<String>): TrustReport {
    val base = target.absoluteFile.parentFile
    fun relative(path: String) = File(path).absoluteFile.relativeTo(base).invariantSeparatorsPath
    val previous = if (!target.exists()) TrustReport(emptyMap()) else try {
        TrustReport.parse(target.readText())
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Cannot update the trust report $target: ${e.message}. Delete it and rebuild the module.", e)
    }
    val updated = previous.updatedWith(report.renamed(::relative), recompiled.mapTo(HashSet(), ::relative)) {
        base.resolve(it).exists()
    }
    Files.createDirectories(base.toPath())
    val temporary = Files.createTempFile(base.toPath(), target.name, ".tmp")
    Files.writeString(temporary, updated.render())
    Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    return updated
}
