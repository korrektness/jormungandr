/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import java.io.File

/**
 * The declaration report of one compilation: a tab-separated file at [target] with a header line and one [Row] per
 * program that the compilation tried to convert. Construction replaces [target]. The values of each column are defined
 * in docs/configuration.md#declaration-reports.
 */
class DeclarationReport(private val target: File) {
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
    ) {
        fun render(): String = listOf(
            file, line.toString(), declaration, program, conversion, conversionKind, conversionReason,
            conversionDetail, verification, verificationDetail, seconds?.let { "%.3f".format(it) }.orEmpty(),
            exceptionCallees,
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
        )
    }
}
