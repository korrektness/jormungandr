/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.names.DomainName
import org.jetbrains.kotlin.formver.core.names.QualifiedDomainFuncName
import org.jetbrains.kotlin.formver.core.names.UnqualifiedDomainFuncName
import org.jetbrains.kotlin.formver.viper.ast.*

/**
 * `equalsRelation(a, b)`, the relation a specification states with Kotlin `a == b` when no `@Pure` function computes
 * it. It is known where `a` is null, where `==` is reference equality, and where `a` is of a type whose values are
 * injected into `Ref`, so that `==` on equal values is reference equality too. [identityTypes] are the runtime types
 * of the classes that do not override `equals`.
 */
class EqualsRelationDomain(identityTypes: List<Exp>) : BuiltinDomain(domainName) {
    companion object {
        private val domainName = DomainName("EqualsRelation")
        private val a = domainVar("a", Type.Ref)
        private val b = domainVar("b", Type.Ref)

        val relation = DomainFunc(
            QualifiedDomainFuncName(domainName, UnqualifiedDomainFuncName("equalsRelation")),
            domainName,
            listOf(a.decl(), b.decl()),
            emptyList(),
            Type.Bool,
            false,
        )
    }

    private val referenceEqualityTypes: List<Exp> = listOf(
        RuntimeTypeDomain.intType(), RuntimeTypeDomain.longType(), RuntimeTypeDomain.shortType(),
        RuntimeTypeDomain.byteType(), RuntimeTypeDomain.boolType(), RuntimeTypeDomain.charType(),
        RuntimeTypeDomain.stringType(), RuntimeTypeDomain.unitType(),
    ) + identityTypes

    override val typeVars: List<Type.TypeVar> = emptyList()
    override val functions: List<DomainFunc> = listOf(relation)
    override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
        axiom("equalsRelationNull") {
            Exp.forall(b) { b ->
                simpleTrigger { relation(RuntimeTypeDomain.nullValue(), b) } eq (b eq RuntimeTypeDomain.nullValue())
            }
        }
        axiom("equalsRelationReference") {
            Exp.forall(a, b) { a, b ->
                assumption { referenceEqualityTypes.map { a isOf it }.reduce { acc, exp -> acc or exp } }
                simpleTrigger { relation(a, b) } eq (a eq b)
            }
        }
    }
}
