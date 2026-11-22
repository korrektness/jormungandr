/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.PrivateSessionConstructor
import org.jetbrains.kotlin.formver.common.TrustLedger
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrustLedgerRetentionTest {
    @OptIn(PrivateSessionConstructor::class)
    private val session = object : FirSession(Kind.Source) {}

    private fun recordCollector(ledger: TrustLedger): WeakReference<TrustedAssumptionCollector> {
        val collector = TrustedAssumptionCollector(session, TypeResolver(), { false }, { null })
        ledger.record("a.kt", "f", collector)
        return WeakReference(collector)
    }

    @Test
    fun `a recorded program's collector is collectable while the ledger lives`() {
        val ledger = TrustLedger()
        val collector = recordCollector(ledger)
        repeat(10) { if (collector.get() != null) System.gc() }
        assertNull(collector.get())
        assertEquals(mapOf("a.kt" to mapOf("f" to emptyList())), ledger.snapshot().files)
    }
}
