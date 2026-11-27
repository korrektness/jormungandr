/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.viper.ast

import org.jetbrains.kotlin.formver.core.names.FixedViperName
import org.jetbrains.kotlin.formver.core.names.SimpleNameResolver
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallableArgTest {
    private val first = Exp.BoolLit(false)
    private val second = Exp.BoolLit(true)
    private val callable = with(SimpleNameResolver()) {
        AstWrapper.Exp(Exp.FuncApp(FixedViperName("f"), listOf(first, second), Type.Bool).toSilver()).asCallable()
    }

    @Test
    fun `returns the argument at the requested zero-based index`() = with(SimpleNameResolver()) {
        assertEquals(AstWrapper.Exp(first.toSilver()), callable.arg(0))
        assertEquals(AstWrapper.Exp(second.toSilver()), callable.arg(1))
    }

    @Test
    fun `reports the one-based ordinal for out-of-range indices`() {
        val cases = mapOf(-1 to 0L, 2 to 3L, Int.MIN_VALUE to -2147483647L, Int.MAX_VALUE to 2147483648L)
        for ((index, ordinal) in cases) {
            val error = assertFailsWith<IllegalArgumentException> { callable.arg(index) }
            val numbers = Regex("-?[0-9]+").findAll(error.message.orEmpty()).map { it.value.toLong() }.toList()
            assertEquals(listOf(ordinal), numbers)
        }
    }
}
