/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.stringInjection
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Function

/**
 * A string literal as a bodiless nullary function whose postconditions give its type, its length, and the character
 * at each index.
 *
 * `stringToRef(Seq(...))` is a string only once the verifier proves the range guard of the `stringToRef` typing
 * axiom, which quantifies over every index, and that proof grows steeply with the length of the literal. Here the
 * type and length are stated outright. The characters are stated without a `Seq` value: Silicon encodes
 * `Seq(c1, ..., cn)` as a chain of appends that its sequence axioms keep expanding, and on a long literal that alone
 * exhausts a failing proof's time.
 *
 * Each character is stated twice. A ground equation per index supplies the term `literal[i]` that a quantifier
 * triggered on indexing a string needs before it can be instantiated at `i`, as a precondition on the characters of
 * a string parameter would. A quantified balanced conditional over the index gives the character at an index that
 * the proof knows only symbolically.
 *
 * The postconditions are assumed rather than checked; they hold because a Kotlin string has at most `Int.MAX_VALUE`
 * characters, each in the `Char` range.
 */
fun stringLiteralFunction(name: SymbolicName, value: String): Function = FunctionBuilder.build(name) {
    returns(Type.Ref)
    val chars = stringInjection.fromRef(result)
    postcondition { result isOf RuntimeTypeDomain.stringType() }
    postcondition { Exp.SeqLength(chars) eq value.length.toExp() }
    value.forEachIndexed { i, char -> postcondition { Exp.SeqIndex(chars, i.toExp()) eq char.code.toExp() } }
    if (value.isNotEmpty()) postcondition {
        Exp.forall(domainVar("index", Type.Int)) { index ->
            assumption { index ge 0.toExp() }
            assumption { index lt value.length.toExp() }
            simpleTrigger { Exp.SeqIndex(chars, index) } eq charAt(value, index, 0, value.length)
        }
    }
}

/** The character of [value] at [index], which lies in `[from, until)`. */
private fun charAt(value: String, index: Exp, from: Int, until: Int): Exp =
    if (until - from == 1) value[from].code.toExp()
    else {
        val middle = (from + until) / 2
        Exp.TernaryExp(index lt middle.toExp(), charAt(value, index, from, middle), charAt(value, index, middle, until))
    }
