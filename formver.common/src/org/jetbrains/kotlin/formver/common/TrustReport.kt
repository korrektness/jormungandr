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
        NEVER_VERIFY("@NeverVerify"),
        NEVER_CONVERT("@NeverConvert"),
        LIBRARY_CONTRACT("library contract"),
    }

    override fun compareTo(other: TrustedAssumption): Int = compareValuesBy(this, other, { it.kind }, { it.name })
}

/**
 * The verified declarations of a module, by source file and then by verification label, each with the trusted
 * assumptions its program used. [render] writes the report file, whose first line is [summary]; [parse] reads it back.
 */
class TrustReport(entries: Map<String, Map<String, Set<TrustedAssumption>>>) {
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
            }
        }
    }

    /**
     * This report with the files of [newer] replaced by its entries. [recompiled] lists the files that the newer
     * compilation covered, so that a file it no longer verifies anything in loses its old entries. Old entries of a
     * file for which [exists] fails are dropped too.
     */
    fun updatedWith(newer: TrustReport, recompiled: Set<String>, exists: (String) -> Boolean): TrustReport =
        TrustReport(entries().filterKeys { it !in recompiled && exists(it) } + newer.entries())

    /** This report with each file name replaced by [rename] of it. */
    fun renamed(rename: (String) -> String): TrustReport = TrustReport(entries().mapKeys { rename(it.key) })

    private fun entries() = files.mapValues { (_, declarations) -> declarations.mapValues { it.value.toSet() } }

    companion object {
        private const val DECLARATION_INDENT = "  "
        private const val ASSUMPTION_INDENT = "    "
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
                        val kind = kindsByLabel[label] ?: malformed()
                        (assumptions ?: malformed()) += TrustedAssumption(kind, name)
                    }
                    line.startsWith(DECLARATION_INDENT) -> {
                        val current = declarations ?: malformed()
                        val label = line.removePrefix(DECLARATION_INDENT)
                        if (label in current) malformed()
                        assumptions = mutableSetOf<TrustedAssumption>().also { current[label] = it }
                    }
                    line.first().isWhitespace() -> malformed()
                    else -> {
                        if (line in entries) malformed()
                        declarations = linkedMapOf<String, MutableSet<TrustedAssumption>>().also { entries[line] = it }
                        assumptions = null
                    }
                }
            }
            return TrustReport(entries)
        }
    }
}

/** Collects the verified declarations of one compilation; checkers on several threads may record into it. */
class TrustLedger {
    private val entries = mutableMapOf<String, MutableMap<String, Set<TrustedAssumption>>>()

    @Synchronized
    fun record(file: String, label: String, assumptions: Set<TrustedAssumption>) {
        entries.getOrPut(file) { mutableMapOf() }[label] = assumptions
    }

    @Synchronized
    fun snapshot(): TrustReport = TrustReport(entries.mapValues { it.value.toMap() })
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
