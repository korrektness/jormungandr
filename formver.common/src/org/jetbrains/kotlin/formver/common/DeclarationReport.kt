/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.formver.common.source.ProofSourceIndex
import java.io.File

/**
 * A compilation's program outcomes in a tab-separated file at [target]. Construction replaces [target].
 * See docs/configuration.md#declaration-reports for row coverage and column definitions.
 */
class DeclarationReport(private val target: File, val sourceIndex: ProofSourceIndex? = null) {
    enum class SyntheticProgramReason { CLASS_OBLIGATION_BUNDLE, DELEGATED_ACCESSOR }

    data class Row(
        val file: String,
        val line: Int,
        val declaration: String,
        val program: String,
        val conversion: String,
        val conversionKind: String = "",
        val conversionReason: String = "",
        val conversionDetail: String = "",
        val verification: String = "",
        val verificationDetail: String = "",
        val seconds: Double? = null,
        val exceptionCallees: String = "",
        val programId: String = "",
        val targetRegionId: String = "",
        val inheritedCandidateRegionIds: String = "",
        val directBodyRegionIds: String = "",
        val syntheticProgramReason: SyntheticProgramReason? = null,
        val contractLambdaCandidates: String = "",
        val specOnlyLambdas: String = "",
        val constructorInitializerCandidates: String = "",
        val enclosingConstructorLinks: String = "",
        val primaryConstructorCandidates: String = "",
        val primaryConstructorLinks: String = "",
    ) {
        fun render(): String = listOf(
            file, line.toString(), declaration, program, conversion, conversionKind, conversionReason,
            conversionDetail, verification, verificationDetail, seconds?.let { "%.3f".format(it) }.orEmpty(),
            exceptionCallees, programId, targetRegionId, inheritedCandidateRegionIds, directBodyRegionIds,
            syntheticProgramReason?.name.orEmpty(), contractLambdaCandidates, specOnlyLambdas,
            constructorInitializerCandidates, enclosingConstructorLinks, primaryConstructorCandidates, primaryConstructorLinks,
        ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
    }

    init {
        target.absoluteFile.parentFile.mkdirs()
        target.writeText(COLUMNS.joinToString("\t") + "\n")
    }

    @Synchronized
    fun write(row: Row) {
        target.appendText(row.render() + "\n")
    }

    companion object {
        val COLUMNS = listOf(
            "file", "line", "declaration", "program", "conversion", "conversion_kind", "conversion_reason",
            "conversion_detail", "verification", "verification_detail", "seconds", "exception_callees",
            "program_id", "target_region_id", "inherited_candidate_region_ids", "direct_body_region_ids",
            "synthetic_program_reason", "contract_lambda_candidates", "spec_only_lambdas",
            "constructor_initializer_candidates", "enclosing_constructor_links",
            "primary_constructor_candidates", "primary_constructor_links",
        )
    }
}
