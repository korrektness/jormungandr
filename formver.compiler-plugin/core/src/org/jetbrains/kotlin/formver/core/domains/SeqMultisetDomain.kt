/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.names.DomainName
import org.jetbrains.kotlin.formver.core.names.QualifiedDomainFuncName
import org.jetbrains.kotlin.formver.core.names.UnqualifiedDomainFuncName
import org.jetbrains.kotlin.formver.viper.ast.*

/**
 * `toMultiset`, the multiset of the elements of a `Seq[Int]`, which Viper does not provide. It gives the contents of
 * an `IntArray`.
 */
object SeqMultisetDomain : BuiltinDomain(DomainName("SeqMultiset")) {
    private val seqType = Type.Seq(Type.Int)
    private val multisetType = Type.Multiset(Type.Int)

    private val s = domainVar("xs", seqType)
    private val t = domainVar("ys", seqType)
    private val i = domainVar("idx", Type.Int)
    private val v = domainVar("elem", Type.Int)

    val toMultiset = DomainFunc(
        QualifiedDomainFuncName(name, UnqualifiedDomainFuncName("toMultiset")),
        name,
        listOf(s.decl()),
        emptyList(),
        multisetType,
        false,
    )

    override val typeVars: List<Type.TypeVar> = emptyList()
    override val functions: List<DomainFunc> = listOf(toMultiset)
    override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
        axiom("toMultisetEmpty") {
            Exp.forall(s) { s ->
                assumption { Exp.SeqLength(s) eq Exp.IntLit(0) }
                simpleTrigger { toMultiset(s) } eq Exp.EmptyMultiset(Type.Int)
            }
        }
        axiom("toMultisetSize") {
            Exp.forall(s) { s ->
                Exp.MultisetSize(simpleTrigger { toMultiset(s) }) eq Exp.SeqLength(s)
            }
        }
        axiom("toMultisetAppend") {
            Exp.forall(s, t) { s, t ->
                simpleTrigger { toMultiset(Exp.SeqAppend(s, t)) } eq Exp.MultisetUnion(toMultiset(s), toMultiset(t))
            }
        }
        // Stated with `setminus`. The equivalent `toMultiset(s[i := v]) union Multiset(s[i]) == toMultiset(s) union
        // Multiset(v)` leaves Z3 to cancel a singleton from both sides of a multiset equation, which it does not do,
        // and then the contents invariant of an in-place insertion sort is not re-established.
        axiom("toMultisetUpdate") {
            Exp.forall(s, i, v) { s, i, v ->
                assumption { i ge Exp.IntLit(0) }
                assumption { i lt Exp.SeqLength(s) }
                simpleTrigger { toMultiset(Exp.SeqUpdate(s, i, v)) } eq Exp.MultisetUnion(
                    Exp.MultisetMinus(toMultiset(s), Exp.ExplicitMultiset(listOf(Exp.SeqIndex(s, i)))),
                    Exp.ExplicitMultiset(listOf(v)),
                )
            }
        }
        axiom("toMultisetMember") {
            Exp.forall(s, i) { s, i ->
                assumption { i ge Exp.IntLit(0) }
                assumption { i lt Exp.SeqLength(s) }
                compoundTrigger {
                    subTrigger { toMultiset(s) }
                    subTrigger { Exp.SeqIndex(s, i) }
                }
                Exp.MultisetCount(Exp.SeqIndex(s, i), toMultiset(s)) gt Exp.IntLit(0)
            }
        }
    }
}
