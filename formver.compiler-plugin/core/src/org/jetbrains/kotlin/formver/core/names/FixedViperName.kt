/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.names

import org.jetbrains.kotlin.formver.viper.AnyName
import org.jetbrains.kotlin.formver.viper.CandidateName
import org.jetbrains.kotlin.formver.viper.SymbolicName

/**
 * A name that a Viper plugin looks up verbatim. It has a single candidate, so name resolution moves colliding names
 * instead.
 */
data class FixedViperName(val name: String) : SymbolicName {
    override val inViper: Boolean = true
    override val candidates: List<CandidateName> = nameOnlyCandidates(name)
    override val children: List<AnyName> = emptyList()
}
