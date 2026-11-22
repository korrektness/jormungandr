/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.formver.common.SemanticOptIn
import org.jetbrains.kotlin.formver.common.TrustLedger
import org.jetbrains.kotlin.formver.common.TrustReport
import org.jetbrains.kotlin.formver.common.TrustedAssumption
import org.jetbrains.kotlin.formver.common.services.updatingTestData
import org.jetbrains.kotlin.formver.core.conversion.TrustedAssumptionCollector
import org.jetbrains.kotlin.formver.core.conversion.record
import org.jetbrains.kotlin.formver.core.lambdaPrograms
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.trust
import org.jetbrains.kotlin.formver.plugin.compiler.verificationLabel
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure
import java.io.File

/**
 * The trust report of a test with the `TRUST_REPORT` directive, compared with its `.trust.txt` golden file. It names
 * each source file by its test file name.
 */
class TrustReportCollector(private val testServices: TestServices) : TestService {
    private val ledger = TrustLedger()

    private val enabled: Boolean
        get() = testServices.moduleStructure.modules.any { FormVerDirectives.TRUST_REPORT in it.directives }

    private val expectedFile: File
        get() {
            val testDataFile = testServices.moduleStructure.originalTestDataFiles.first()
            return testDataFile.parentFile.resolve("${testDataFile.nameWithoutExtension}.trust.txt")
        }

    /** Records the programs of [decl], a verification target of the source file [file] whose verification succeeded. */
    fun record(file: String, decl: FirDeclaration) {
        if (!enabled) return
        for ((label, trust) in programTrust(decl)) {
            ledger.record(file, label, trust)
        }
    }

    fun assertEquality() {
        val expectedFile = expectedFile
        if (!enabled) {
            testServices.assertions.assertFalse(expectedFile.exists()) { "${expectedFile.name} exists without TRUST_REPORT" }
            return
        }
        val actual = ledger.snapshot().render()
        // assertEqualsToFile creates a missing golden file, so a check run would record the report and the next run pass.
        if (!updatingTestData && !expectedFile.exists()) {
            testServices.assertions.assertEquals("", actual) { "${expectedFile.name} does not exist, but the test has TRUST_REPORT" }
        }
        testServices.assertions.assertEqualsToFile(expectedFile, actual)
    }

    /**
     * Compares the declarations of the golden file with the assumptions that conversion collected for them, without
     * verifying. The summary line and which declarations verify are outside this check: a verification target missing
     * from the golden file passes, and a listed declaration that is not a verification target fails. Declarations
     * sharing a label match the golden entry when any one of them does. A missing golden file is left to
     * [assertEquality].
     */
    fun assertConversionEquality(info: FirOutputArtifact) {
        val expectedFile = expectedFile
        if (!enabled) {
            testServices.assertions.assertFalse(expectedFile.exists()) { "${expectedFile.name} exists without TRUST_REPORT" }
            return
        }
        if (!expectedFile.exists()) return
        val expectedText = expectedFile.readText()
        val expected = TrustReport.parse(expectedText)
        val candidates = mutableMapOf<Pair<String, String>, MutableList<Candidate>>()
        for (part in info.partsForDependsOnModules) {
            for ((testFile, firFile) in part.firFiles) {
                for (decl in verificationTargets(firFile).filter { it !is FirFile }) {
                    for ((label, trust) in programTrust(decl)) {
                        candidates.getOrPut(testFile.name to label) { mutableListOf() } += Candidate(trust)
                    }
                }
            }
        }
        val entries = mutableMapOf<String, MutableMap<String, Set<TrustedAssumption>>>()
        val modes = mutableMapOf<String, MutableMap<String, Set<SemanticOptIn>>>()
        for ((file, declarations) in expected.files) {
            for ((label, assumptions) in declarations) {
                val found = candidates[file to label] ?: continue
                val wantedModes = expected.semanticOptIns[file]?.get(label).orEmpty().toSet()
                val chosen = found.firstOrNull { it.assumptions == assumptions.toSet() && it.modes == wantedModes } ?: found.first()
                entries.getOrPut(file) { mutableMapOf() }[label] = chosen.assumptions
                modes.getOrPut(file) { mutableMapOf() }[label] = chosen.modes
            }
        }
        val actual = TrustReport(entries, modes).render()
        testServices.assertions.assertEquals(expectedText.substringAfter('\n'), actual.substringAfter('\n')) {
            "${expectedFile.name} differs from the converted assumptions"
        }
    }

    private fun programTrust(decl: FirDeclaration): List<Pair<String, TrustedAssumptionCollector>> =
        listOfNotNull(if (decl.shouldVerify == true) decl.verificationLabel to decl.trust!! else null) +
            decl.lambdaPrograms.orEmpty().filter { it.shouldVerify }.map { it.label to it.trust }

    private class Candidate(trust: TrustedAssumptionCollector) {
        val assumptions: Set<TrustedAssumption> = trust.assumptions
        val modes: Set<SemanticOptIn> = trust.semanticOptIns.toSet()
    }
}
