/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.names.FixedViperName
import org.jetbrains.kotlin.formver.viper.ast.*

/**
 * The well-founded orders that Silver's termination plugin checks measures against, as the `.vpr` files in its
 * `import/decreases` resources declare them. The plugin finds `decreasing`, `bounded`, `nestedPredicates` and the
 * `PredicateInstance` domain by name.
 */
object TerminationDomains {
    private val t = Type.TypeVar("T")

    object WellFoundedOrder : BuiltinDomain(FixedViperName("WellFoundedOrder")) {
        val decreasing = DomainFunc(FixedViperName("decreasing"), name,
            listOf(domainVar("arg1", t).decl(), domainVar("arg2", t).decl()), listOf(t), Type.Bool, false)
        val bounded = DomainFunc(FixedViperName("bounded"), name, listOf(domainVar("arg1", t).decl()), listOf(t), Type.Bool, false)

        override val typeVars: List<Type.TypeVar> = listOf(t)
        override val functions: List<DomainFunc> = listOf(decreasing, bounded)
        override val axioms: List<DomainAxiom> = emptyList()
    }

    private fun decreasing(type: Type, a: Exp, b: Exp) = WellFoundedOrder.decreasing.toFuncApp(listOf(a, b)).withArg(type)
    private fun bounded(type: Type, a: Exp) = WellFoundedOrder.bounded.toFuncApp(listOf(a)).withArg(type)
    private fun Exp.DomainFuncApp.withArg(type: Type) = copy(typeVarMap = mapOf(t to type))

    object IntWellFoundedOrder : BuiltinDomain(FixedViperName("IntWellFoundedOrder")) {
        override val typeVars: List<Type.TypeVar> = emptyList()
        override val functions: List<DomainFunc> = emptyList()
        override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
            axiom("integer_ax_dec") {
                Exp.forall(domainVar("int1", Type.Int), domainVar("int2", Type.Int)) { a, b ->
                    assumption { a lt b }
                    simpleTrigger { decreasing(Type.Int, a, b) }
                }
            }
            axiom("integer_ax_bound") {
                Exp.forall(domainVar("int1", Type.Int)) { a ->
                    assumption { a ge Exp.IntLit(0) }
                    simpleTrigger { bounded(Type.Int, a) }
                }
            }
        }
    }

    object BoolWellFoundedOrder : BuiltinDomain(FixedViperName("BoolWellFoundedOrder")) {
        override val typeVars: List<Type.TypeVar> = emptyList()
        override val functions: List<DomainFunc> = emptyList()
        override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
            axiom("bool_ax_dec") { decreasing(Type.Bool, Exp.BoolLit(false), Exp.BoolLit(true)) }
            axiom("bool_ax_bound") {
                Exp.forall(domainVar("bool1", Type.Bool)) { b -> simpleTrigger { bounded(Type.Bool, b) } }
            }
        }
    }

    object PredicateInstance : BuiltinDomain(FixedViperName("PredicateInstance")) {
        val type = Type.Domain(name)
        override val typeVars: List<Type.TypeVar> = emptyList()
        override val functions: List<DomainFunc> = emptyList()
        override val axioms: List<DomainAxiom> = emptyList()
    }

    object PredicateInstancesNestedRelation : BuiltinDomain(FixedViperName("PredicateInstancesNestedRelation")) {
        val nestedPredicates = DomainFunc(FixedViperName("nestedPredicates"), name,
            listOf(domainVar("l1", PredicateInstance.type).decl(), domainVar("l2", PredicateInstance.type).decl()),
            emptyList(), Type.Bool, false)

        override val typeVars: List<Type.TypeVar> = emptyList()
        override val functions: List<DomainFunc> = listOf(nestedPredicates)
        override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
            axiom("nestedTrans") {
                Exp.forall(instance("l1"), instance("l2"), instance("l3")) { l1, l2, l3 ->
                    compoundTrigger {
                        subTrigger { nestedPredicates(l1, l2) }
                        subTrigger { nestedPredicates(l2, l3) }
                    }
                    assumption { nestedPredicates(l1, l2) and nestedPredicates(l2, l3) }
                    nestedPredicates(l1, l3)
                }
            }
            axiom("nestedReflex") {
                Exp.forall(instance("l1")) { l1 -> Exp.Not(simpleTrigger { nestedPredicates(l1, l1) }) }
            }
        }
    }

    object PredicateInstancesWellFoundedOrder : BuiltinDomain(FixedViperName("PredicateInstancesWellFoundedOrder")) {
        override val typeVars: List<Type.TypeVar> = emptyList()
        override val functions: List<DomainFunc> = emptyList()
        override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
            axiom("predicate_instances_ax_dec") {
                Exp.forall(instance("l1"), instance("l2")) { l1, l2 ->
                    val nested = simpleTrigger { PredicateInstancesNestedRelation.nestedPredicates(l1, l2) }
                    Exp.And(Exp.Implies(decreasing(PredicateInstance.type, l1, l2), nested),
                        Exp.Implies(nested, decreasing(PredicateInstance.type, l1, l2)))
                }
            }
            axiom("predicate_instances_ax_bound") {
                Exp.forall(instance("l1")) { l1 -> simpleTrigger { bounded(PredicateInstance.type, l1) } }
            }
        }
    }

    private fun instance(name: String) = domainVar(name, PredicateInstance.type)

    /** The domains a program needs when its measures have the given [types]. */
    fun required(types: Set<Type>): List<Domain> = buildList {
        if (types.isEmpty()) return@buildList
        add(WellFoundedOrder)
        if (Type.Int in types) add(IntWellFoundedOrder)
        if (Type.Bool in types) add(BoolWellFoundedOrder)
        if (PredicateInstance.type in types) {
            addAll(listOf(PredicateInstance, PredicateInstancesNestedRelation, PredicateInstancesWellFoundedOrder))
        }
    }
}
