/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.names

import net.jqwik.api.Example
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShortNameResolverTest {
    @Example
    fun `nested class and underscore class members have distinct names after candidate exhaustion`() {
        val members = listOf("A.B", "A_B").map { className ->
            ScopedName(
                ClassScope(PackageScope(FqName.ROOT), ClassKotlinName(FqName(className))),
                GetterKotlinName(Name.identifier("value")),
            )
        }
        val probes = members.map(::VacuityProbeName)
        val resolver = ShortNameResolver()
        (members + probes).forEach(resolver::register)
        resolver.resolve()

        val names = (members + probes).map(resolver::lookup)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_$]*")) })
        resolver.resolve()
        assertEquals(names, (members + probes).map(resolver::lookup))
    }

    @Example
    fun `fallback avoids occupied suffixes and propagates to dependent names`() {
        val source = ScopedName(FakeScope, SimpleKotlinName(Name.identifier("reserved")))
        val fixed = FixedViperName("reserved")
        val occupiedSuffix = FixedViperName("reserved\$0")
        val probe = VacuityProbeName(source)
        val dependentCollision = FixedViperName("vacuity_reserved\$1")
        val all: List<SymbolicName> = listOf(source, fixed, occupiedSuffix, probe, dependentCollision)
        val resolver = ShortNameResolver()
        all.forEach(resolver::register)
        resolver.resolve()

        assertEquals(all.size, all.map(resolver::lookup).toSet().size)
        listOf(fixed, occupiedSuffix, dependentCollision).forEach { assertEquals(it.name, resolver.lookup(it)) }
        assertNotEquals("reserved", resolver.lookup(source))

        val later = FixedViperName(resolver.lookup(source))
        resolver.register(later)
        resolver.resolve()
        assertEquals(later.name, resolver.lookup(later))
        assertEquals(all.size + 1, (all + later).map(resolver::lookup).toSet().size)
    }

    @Example
    fun `fixed names cannot claim a Viper keyword`() {
        val resolver = ShortNameResolver()
        resolver.register(FixedViperName("result"))
        assertFailsWith<IllegalStateException> { resolver.resolve() }
    }

    @Example
    fun `ordinary candidate expansion preserves short names and keywords`() {
        val name = Name.identifier("result").embedParameterName()
        val resolver = ShortNameResolver()
        resolver.register(name)
        resolver.resolve()
        assertEquals("par_result", resolver.lookup(name))
        assertEquals("result", resolver.lookup(FunctionResultVariableName))
    }
}
