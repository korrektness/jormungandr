/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.formver.common.TrustLedger
import org.jetbrains.kotlin.formver.core.conversion.TrustedAssumptionCollector
import org.jetbrains.kotlin.formver.common.services.updatingTestData
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure

/**
 * The trust report of a test with the `TRUST_REPORT` directive, compared with its `.trust.txt` golden file. It names
 * each source file by its test file name.
 */
class TrustReportCollector(private val testServices: TestServices) : TestService {
    private val ledger = TrustLedger()

    private val enabled: Boolean
        get() = testServices.moduleStructure.modules.any { FormVerDirectives.TRUST_REPORT in it.directives }

    fun record(file: String, label: String, trust: TrustedAssumptionCollector) {
        if (enabled) ledger.record(file, label, { trust.semanticOptIns }) { trust.assumptions }
    }

    fun assertEquality() {
        val testDataFile = testServices.moduleStructure.originalTestDataFiles.first()
        val expectedFile = testDataFile.parentFile.resolve("${testDataFile.nameWithoutExtension}.trust.txt")
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
}
