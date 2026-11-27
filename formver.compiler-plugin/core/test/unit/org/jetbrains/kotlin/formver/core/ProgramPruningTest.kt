/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core

import net.jqwik.api.Example
import org.jetbrains.kotlin.formver.core.names.FixedViperName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Domain
import org.jetbrains.kotlin.formver.viper.ast.DomainAxiom
import org.jetbrains.kotlin.formver.viper.ast.DomainFunc
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Predicate
import org.jetbrains.kotlin.formver.viper.ast.Program
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.UserFunction
import org.jetbrains.kotlin.formver.viper.ast.UserMethod
import org.jetbrains.kotlin.formver.viper.ast.withoutUncalledFunctions
import kotlin.test.assertEquals

class ProgramPruningTest {
    @Example
    fun `follows reachable cycles without retaining disconnected cycles or reordering functions`() {
        val first = function("first", body = call("second"))
        val second = function("second", body = call("first"))
        val deadFirst = function("deadFirst", body = call("deadSecond"))
        val deadSecond = function("deadSecond", body = call("deadFirst"))
        val root = function("root", pres = listOf(call("first")))
        val uncalledRoot = function("uncalledRoot")
        val input = program(listOf(second, deadFirst, uncalledRoot, first, deadSecond, root))
        val removable = setOf(first.name, second.name, deadFirst.name, deadSecond.name)

        assertEquals(listOf(second, uncalledRoot, first, root), input.withoutUncalledFunctions(removable).functions)
    }

    @Example
    fun `uses domain axioms predicate bodies and method contracts and bodies as roots`() {
        val axiom = function("axiom")
        val predicate = function("predicate")
        val methodPre = function("methodPre")
        val methodPost = function("methodPost")
        val methodBody = function("methodBody")
        val dead = function("dead")
        val domain = object : Domain(FixedViperName("domain")) {
            override val typeVars: List<Type.TypeVar> = emptyList()
            override val functions: List<DomainFunc> = emptyList()
            override val axioms = listOf(DomainAxiom(null, name, axiom()))
        }
        val method = UserMethod(
            FixedViperName("method"),
            emptyList(),
            Declaration.LocalVarDecl(FixedViperName("result"), Type.Bool),
            pres = listOf(methodPre()),
            posts = listOf(methodPost()),
            body = Stmt.Seqn(listOf(Stmt.Assert(methodBody()))),
        )
        val retained = listOf(methodBody, axiom, methodPre, predicate, methodPost)
        val input = program(retained + dead).copy(
            domains = listOf(domain),
            methods = listOf(method),
            predicates = listOf(Predicate(FixedViperName("p"), emptyList(), predicate())),
        )

        assertEquals(retained, input.withoutUncalledFunctions(input.functions.map { it.name }.toSet()).functions)
    }

    private fun program(functions: List<UserFunction>) = Program(
        domains = emptyList(),
        fields = emptyList(),
        functions = functions,
        predicates = emptyList(),
        methods = emptyList(),
        adts = emptyList(),
    )

    private fun function(name: String, pres: List<Exp> = emptyList(), body: Exp? = null) =
        UserFunction(FixedViperName(name), emptyList(), Type.Bool, pres, emptyList(), body)

    private fun call(name: String) = Exp.FuncApp(FixedViperName(name), emptyList(), Type.Bool)
}
