/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Type

/** A trusted consuming change of presentation of one dynamic object's owned footprint. */
class FamilyTransfer(from: ClassTypeEmbedding, to: ClassTypeEmbedding) {
    val method: Method = object : Method(ScopedName(from.name.asScope(), ScopedName(to.name.asScope(), PredicateName("upcast")))) {
        override val formalArgs = listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref))
        override val formalReturns = emptyList<Declaration.LocalVarDecl>()
        private val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
        override val pres = listOf(Exp.PredicateAccess(from.uniquePredicateName, listOf(receiver), PermExp.FullPerm()))
        override val posts = listOf(Exp.PredicateAccess(to.uniquePredicateName, listOf(receiver), PermExp.FullPerm()))
    }

    /** Restores a concrete presentation only for an object known to have that runtime type. */
    val restore: Method by lazy {
        object : Method(ScopedName(from.name.asScope(), ScopedName(to.name.asScope(), PredicateName("restore")))) {
            override val formalArgs = listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref))
            override val formalReturns = emptyList<Declaration.LocalVarDecl>()
            private val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
            override val pres = listOf(
                Exp.PredicateAccess(to.uniquePredicateName, listOf(receiver), PermExp.FullPerm()),
                RuntimeTypeDomain.isSubtype(RuntimeTypeDomain.typeOf(receiver), from.runtimeType),
            )
            override val posts = listOf(Exp.PredicateAccess(from.uniquePredicateName, listOf(receiver), PermExp.FullPerm()))
        }
    }
}
