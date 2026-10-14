/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.viper.ast

import org.jetbrains.kotlin.formver.viper.*

data class Program(
    val domains: List<Domain>,
    val fields: List<Field>,
    val functions: List<Function>,
    val predicates: List<Predicate>,
    val methods: List<Method>,
    val adts: List<AdtDecl>,
    /* no extensions */
    val pos: Position = Position.NoPosition,
    val info: Info = Info.NoInfo,
) : IntoSilver<viper.silver.ast.Program> {
    context(nameResolver: NameResolver)
    override fun toSilver(): viper.silver.ast.Program = viper.silver.ast.Program(
        domains.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        fields.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        functions.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        predicates.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        methods.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        adts.sortedBy { it.name.mangled }.toSilver().toScalaSeq(),
        pos.toSilver(),
        info.toSilver(),
        silverNoTrafos,
    )

    fun toShort(): Program = Program(
        domains.filter { it.includeInShortDump },
        fields.filter { it.includeInShortDump },
        functions.filter { it.includeInDumpPolicy != IncludeInDumpPolicy.ONLY_IN_FULL_DUMP },
        predicates.filter { it.includeInDumpPolicy != IncludeInDumpPolicy.ONLY_IN_FULL_DUMP },
        methods.filter { it.includeInShortDump },
        adts.filter { it.includeInShortDump },
        pos,
        info,
    )

    fun withoutPredicates(): Program = copy(
        predicates = predicates.filter { it.includeInDumpPolicy == IncludeInDumpPolicy.ALWAYS },
        functions = functions.filter { it.includeInDumpPolicy == IncludeInDumpPolicy.ALWAYS }
    )

    context(nameResolver: NameResolver)
    fun toDebugOutput(): String = toSilver().toString()
}

context(nameResolver: NameResolver)
fun Program.registerAllNames() {
    domains.forEach { domain ->
        nameResolver.register(domain.name)
        domain.functions.forEach { function ->
            nameResolver.register(function.name)
            function.formalArgs.forEach { arg -> nameResolver.register(arg.name) }
        }
        domain.axioms.forEach { axiom ->
            axiom.name?.let { nameResolver.register(it) }
            axiom.exp.registerNames()
        }
    }
    fields.forEach { nameResolver.register(it.name) }

    functions.forEach { it.registerNames() }

    predicates.forEach { predicate ->
        nameResolver.register(predicate.name)
        predicate.formalArgs.forEach { arg -> nameResolver.register(arg.name) }
        predicate.body?.registerNames()
    }

    methods.forEach { method ->
        nameResolver.register(method.name)
        method.formalArgs.forEach { arg -> nameResolver.register(arg.name) }
        method.formalReturns.forEach { ret -> nameResolver.register(ret.name) }
        method.pres.forEach { it.registerNames() }
        method.posts.forEach { it.registerNames() }
        method.body?.registerNames()
    }

    adts.forEach { adt ->
        nameResolver.register(adt.name)
        adt.constructors.forEach { constructor ->
            nameResolver.register(constructor.name)
            constructor.formalArgs.forEach { nameResolver.register(it.name) }
        }
    }
}

context(nameResolver: NameResolver)
private fun Function.registerNames() {
    nameResolver.register(name)
    formalArgs.forEach { arg -> nameResolver.register(arg.name) }
    pres.forEach { it.registerNames() }
    posts.forEach { it.registerNames() }
    body?.registerNames()
}

/**
 * Drops each function named in [removable] that no domain, method, predicate or kept function calls.
 * Silicon checks every function's definition, so an uncalled function costs time and adds no fact the rest of the
 * program can use.
 */
fun Program.withoutUncalledFunctions(removable: Set<SymbolicName>): Program {
    val candidates = functions.filter { it.name in removable }.associateBy { it.name }
    val called = mutableSetOf<SymbolicName>()
    val pending = ArrayDeque<Function>()
    // Every name the emitted program uses is registered, so registration sees every call.
    val collector = object : NameResolver {
        override fun lookup(name: SymbolicName): String = error("Names are only collected here.")
        override fun resolve() {}
        override fun register(name: AnyName) {
            val function = candidates[name] ?: return
            if (called.add(function.name)) pending.add(function)
        }
    }
    with(collector) {
        copy(functions = functions.filter { it.name !in candidates }).registerAllNames()
        while (pending.isNotEmpty()) pending.removeFirst().registerNames()
    }
    return copy(functions = functions.filter { it.name !in candidates || it.name in called })
}
