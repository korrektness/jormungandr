/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.viper.ast

import org.jetbrains.kotlin.formver.viper.IntoSilver
import org.jetbrains.kotlin.formver.viper.NameResolver
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.mangled
import org.jetbrains.kotlin.formver.viper.toScalaSeq
import org.jetbrains.kotlin.formver.viper.toSilver

sealed class PermExp : IntoSilver<viper.silver.ast.PermExp> {
    data class WildcardPerm(
        val pos: Position = Position.NoPosition,
        val info: Info = Info.NoInfo,
    ) : PermExp() {
        context(nameResolver: NameResolver)
        override fun toSilver(): viper.silver.ast.PermExp =
            viper.silver.ast.WildcardPerm(pos.toSilver(), info.toSilver(), silverNoTrafos)
    }

    data class FullPerm(
        val pos: Position = Position.NoPosition,
        val info: Info = Info.NoInfo,
    ) : PermExp() {
        context(nameResolver: NameResolver)
        override fun toSilver(): viper.silver.ast.PermExp =
            viper.silver.ast.FullPerm(pos.toSilver(), info.toSilver(), silverNoTrafos)
    }

    /** The permission currently held to the predicate instance [predicateName] of [args], `perm(P(args))`. */
    data class CurrentPredicatePerm(
        val predicateName: SymbolicName,
        val args: List<Exp>,
        val pos: Position = Position.NoPosition,
        val info: Info = Info.NoInfo,
    ) : PermExp() {
        context(nameResolver: NameResolver)
        override fun toSilver(): viper.silver.ast.PermExp =
            viper.silver.ast.CurrentPerm(
                viper.silver.ast.PredicateAccess(
                    args.toSilver().toScalaSeq(), predicateName.mangled, pos.toSilver(), info.toSilver(), silverNoTrafos,
                ),
                pos.toSilver(), info.toSilver(), silverNoTrafos,
            )
    }
}
